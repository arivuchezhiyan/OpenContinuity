/**
 * Discovery Manager - handles mDNS service discovery + UDP broadcast beacon
 * Works on same-router Wi-Fi, phone hotspot, laptop hotspot, USB tethering.
 */

import Bonjour, { Browser, Service } from 'bonjour-service';
import { EventEmitter } from 'events';
import dgram from 'dgram';
import net from 'net';
import os from 'os';
import { execSync } from 'child_process';

export interface DiscoveredDevice {
  name: string;
  host: string;
  port: number;
  platform: string;
  lastSeen: number;
}

const BEACON_PORT = 8768;
const WS_PORT = 8765;

/** Extra well-known Android hotspot gateway IPs (fallback if routing table parse fails) */
const FALLBACK_GATEWAY_IPS = [
  '192.168.43.1',   // Standard Android hotspot
  '10.42.0.1',      // Samsung / some Linux hotspot
  '192.168.137.1',  // Windows Mobile Hotspot
  '172.20.10.1',    // iPhone Personal Hotspot
];

/**
 * Parse Windows routing table to find real IPv4 gateways (excludes VPN 0.0.0.0 entries).
 * `route print 0.0.0.0` on Windows outputs lines like:
 *   0.0.0.0          0.0.0.0     10.48.185.73   10.48.185.183      25
 */
function getRoutingTableGateways(): string[] {
  try {
    const out = execSync('route print 0.0.0.0', { encoding: 'utf8', timeout: 3_000 });
    const gateways = new Set<string>();
    for (const line of out.split('\n')) {
      const parts = line.trim().split(/\s+/);
      if (parts.length >= 3 && parts[0] === '0.0.0.0' && parts[1] === '0.0.0.0') {
        const gw = parts[2];
        if (gw && gw !== '0.0.0.0' && net.isIPv4(gw)) gateways.add(gw);
      }
    }
    return [...gateways];
  } catch {
    return [];
  }
}

/**
 * Return all non-loopback, non-APIPA, non-VPN IPv4 addresses on this machine.
 * We bind one UDP socket per address so that VPN adapters don't steal broadcasts.
 */
function getPhysicalIPv4s(): string[] {
  const ifaces = os.networkInterfaces();
  const ips: string[] = [];
  for (const [name, addrs] of Object.entries(ifaces)) {
    if (!addrs) continue;
    if (/vpn|tun|tap|proton|wireguard/i.test(name)) continue;
    for (const addr of addrs) {
      if (
        addr.family === 'IPv4' &&
        !addr.internal &&
        !addr.address.startsWith('169.254')
      ) {
        ips.push(addr.address);
      }
    }
  }
  return ips;
}

export class DiscoveryManager extends EventEmitter {
  private bonjour: Bonjour;
  private browser: Browser | null = null;
  private devices: Map<string, DiscoveredDevice> = new Map();
  private isDiscovering = false;
  private udpSockets: dgram.Socket[] = [];

  constructor() {
    super();
    this.bonjour = new Bonjour();
  }

  startDiscovery(): void {
    if (this.isDiscovering) {
      console.log('Discovery already running — restarting to flush stale devices');
      this.stopDiscovery();
    }

    // Clear all cached devices so stale IPs from a previous network don't linger
    this.devices.clear();
    this.emit('devicesCleared');

    console.log('Starting discovery (mDNS + UDP beacon + gateway probe)...');
    this.isDiscovering = true;

    // mDNS — works on same-router Wi-Fi
    this.browser = this.bonjour.find({ type: 'opencontinuity' }, (service: Service) => {
      this.handleServiceFound(service);
    });
    this.bonjour.find({ type: 'tcp' }, (service: Service) => {
      if (service.name.includes('OpenContinuity')) this.handleServiceFound(service);
    });

    // UDP beacon listener — primary path for hotspot / tethering
    this.startUdpListeners();

    // Probe actual gateways from routing table + known fallbacks
    this.probeGateways();
  }

  stopDiscovery(): void {
    if (this.browser) {
      this.browser.stop();
      this.browser = null;
    }
    for (const sock of this.udpSockets) {
      try { sock.close(); } catch (_) {}
    }
    this.udpSockets = [];
    this.isDiscovering = false;
    console.log('Discovery stopped');
  }

  /**
   * Bind one UDP socket per physical network interface.
   * Binding to a specific local IP pins the socket to that interface,
   * so a running VPN (which captures 0.0.0.0) cannot swallow the packet.
   */
  private startUdpListeners(): void {
    for (const sock of this.udpSockets) {
      try { sock.close(); } catch (_) {}
    }
    this.udpSockets = [];

    const localIPs = getPhysicalIPv4s();
    // Always add 0.0.0.0 as a last-resort catch-all
    const bindTargets = localIPs.length > 0 ? localIPs : ['0.0.0.0'];

    console.log('Binding UDP beacon listener on:', bindTargets);

    for (const localIP of bindTargets) {
      const sock = dgram.createSocket({ type: 'udp4', reuseAddr: true });

      sock.on('message', (msg, rinfo) => {
        try {
          const data = JSON.parse(msg.toString('utf8'));
          if (data.type !== 'opencontinuity') return;
          // Use the actual source IP — works correctly even through NAT
          const host = rinfo.address;
          const port: number = data.port ?? WS_PORT;
          const name: string = data.deviceName ?? 'Android';
          const platform: string = data.platform ?? 'android';
          console.log(`UDP beacon received from ${host} on interface ${localIP}`);
          this.registerDevice({ name, host, port, platform });
        } catch (_) { /* ignore malformed packets */ }
      });

      sock.on('error', (err) => {
        console.warn(`UDP socket (${localIP}) error:`, err.message);
      });

      sock.bind(BEACON_PORT, localIP === '0.0.0.0' ? '0.0.0.0' : localIP, () => {
        try { sock.setBroadcast(true); } catch (_) {}
        console.log(`UDP listener ready on ${localIP}:${BEACON_PORT}`);
      });

      this.udpSockets.push(sock);
    }
  }

  /**
   * TCP-probe the real gateway IPs from the routing table (dynamic) + fallback list.
   * If the port is open, register as a discovered device immediately without waiting
   * for the next 5-second beacon cycle.
   */
  private probeGateways(): void {
    const dynamic = getRoutingTableGateways();
    const all = [...new Set([...dynamic, ...FALLBACK_GATEWAY_IPS])];

    console.log('Probing gateway IPs:', all);

    for (const ip of all) {
      const tcpSock = new net.Socket();
      tcpSock.setTimeout(2_000);
      tcpSock.connect(WS_PORT, ip, () => {
        tcpSock.destroy();
        console.log(`Gateway probe HIT: ${ip}:${WS_PORT} — registering as Android device`);
        this.registerDevice({ name: 'Android (gateway probe)', host: ip, port: WS_PORT, platform: 'android' });
      });
      tcpSock.on('error', () => tcpSock.destroy());
      tcpSock.on('timeout', () => tcpSock.destroy());
    }
  }

  /** Register (or refresh) a discovered device and emit events. */
  private registerDevice({ name, host, port, platform }: {
    name: string; host: string; port: number; platform: string;
  }): void {
    const key = `${host}:${port}`;
    const isNew = !this.devices.has(key);
    const device: DiscoveredDevice = { name, host, port, platform, lastSeen: Date.now() };
    this.devices.set(key, device);
    if (isNew) {
      console.log(`Device registered: ${name} at ${host}:${port}`);
      this.emit('deviceFound', device);
    } else {
      this.emit('deviceUpdated', device);
    }
  }

  private handleServiceFound(service: Service): void {
    const deviceName = service.txt?.deviceName || service.name.replace('OpenContinuity-', '');
    const platform = service.txt?.platform || 'android';

    // Prefer IPv4 address — skip link-local IPv6 (fe80::)
    const addresses = service.addresses || [];
    const ipv4 = addresses.find((a: string) => !a.includes(':'));
    const host = ipv4 || addresses.find((a: string) => !a.startsWith('fe80')) || service.host;
    if (!host) {
      console.log('Service found but no usable host address:', service.name);
      return;
    }
    this.registerDevice({ name: deviceName, host, port: service.port, platform });
  }

  getDiscoveredDevices(): DiscoveredDevice[] {
    const now = Date.now();
    const fresh: DiscoveredDevice[] = [];
    this.devices.forEach((device, key) => {
      if (now - device.lastSeen < 5 * 60 * 1_000) {
        fresh.push(device);
      } else {
        this.devices.delete(key);
      }
    });
    return fresh;
  }

  // Manual device addition for direct connection
  addManualDevice(host: string, port: number): DiscoveredDevice {
    this.registerDevice({ name: `Manual (${host})`, host, port, platform: 'unknown' });
    return this.devices.get(`${host}:${port}`)!;
  }

  destroy(): void {
    this.stopDiscovery();
    this.bonjour.destroy();
  }
}
