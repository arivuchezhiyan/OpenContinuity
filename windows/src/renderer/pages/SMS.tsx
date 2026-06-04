import React, { useState, useEffect, useRef } from 'react';
import { useConnection } from '../contexts/ConnectionContext';

interface Conversation {
  threadId: number;
  address: string;
  contactName: string;
  lastMessage: string;
  lastMessageTime: number;
  unreadCount: number;
}

interface Message {
  id: number;
  address: string;
  body: string;
  date: number;
  type: 'incoming' | 'outgoing';
}

function SMS() {
  const { connectionState } = useConnection();
  const [conversations, setConversations] = useState<Conversation[]>([]);
  const [selectedThread, setSelectedThread] = useState<number | null>(null);
  const [messages, setMessages] = useState<Message[]>([]);
  const [newMessage, setNewMessage] = useState('');
  const [searchQuery, setSearchQuery] = useState('');
  const messagesEndRef = useRef<HTMLDivElement>(null);

  const isConnected = connectionState.status === 'connected';

  useEffect(() => {
    if (isConnected) {
      loadConversations();
    }

    // Listen for new SMS
    const removeListener = window.api.onSmsReceived?.((sms: any) => {
      // Refresh conversations
      loadConversations();
      
      // If we're viewing this conversation, add the message
      if (selectedThread) {
        loadMessages(selectedThread);
      }
    });

    return () => {
      removeListener?.();
    };
  }, [isConnected, selectedThread]);

  useEffect(() => {
    scrollToBottom();
  }, [messages]);

  const loadConversations = async () => {
    try {
      const convs = await window.api.sms.getConversations();
      setConversations(convs || []);
    } catch (error) {
      console.error('Failed to load conversations:', error);
    }
  };

  const loadMessages = async (threadId: number) => {
    try {
      const msgs = await window.api.sms.getMessages(threadId);
      setMessages(msgs || []);
    } catch (error) {
      console.error('Failed to load messages:', error);
    }
  };

  const handleSelectThread = (threadId: number) => {
    setSelectedThread(threadId);
    loadMessages(threadId);
  };

  const handleSendMessage = async () => {
    if (!newMessage.trim() || !selectedThread) return;

    const conversation = conversations.find(c => c.threadId === selectedThread);
    if (!conversation) return;

    try {
      await window.api.sms.send(conversation.address, newMessage);
      setNewMessage('');
      
      // Add optimistic message
      setMessages(prev => [...prev, {
        id: Date.now(),
        address: conversation.address,
        body: newMessage,
        date: Date.now(),
        type: 'outgoing'
      }]);
    } catch (error) {
      console.error('Failed to send message:', error);
    }
  };

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  };

  const formatTime = (timestamp: number): string => {
    const date = new Date(timestamp);
    const now = new Date();
    const diff = now.getTime() - date.getTime();

    if (diff < 24 * 60 * 60 * 1000) {
      return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    } else if (diff < 7 * 24 * 60 * 60 * 1000) {
      return date.toLocaleDateString([], { weekday: 'short' });
    } else {
      return date.toLocaleDateString([], { month: 'short', day: 'numeric' });
    }
  };

  const filteredConversations = conversations.filter(conv =>
    conv.contactName.toLowerCase().includes(searchQuery.toLowerCase()) ||
    conv.address.includes(searchQuery)
  );

  const selectedConversation = conversations.find(c => c.threadId === selectedThread);

  return (
    <div className="h-full flex flex-col animate-fade-in font-inter">
      <div className="mb-4">
        <h1 className="text-headline-xl text-on-surface mb-2">SMS</h1>
        <p className="text-body-lg text-on-surface-variant">
          Send and receive text messages from your PC
        </p>
      </div>

      {!isConnected ? (
        <div className="glass-panel p-8 text-center">
          <span className="material-symbols-outlined text-primary/50 text-5xl mb-4 block">sms</span>
          <p className="text-body-lg text-on-surface-variant">
            Please connect to a device first to access SMS
          </p>
        </div>
      ) : (
        <div className="flex-1 flex glass-panel overflow-hidden">
          {/* Conversations List */}
          <div className="w-80 border-r border-white/[0.08] flex flex-col">
            {/* Search */}
            <div className="p-3 border-b border-white/[0.08]">
              <div className="relative">
                <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-on-surface-variant text-lg">search</span>
                <input
                  type="text"
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  placeholder="Search conversations..."
                  className="w-full pl-10 pr-4 py-2.5 glass-input text-body-md placeholder:text-on-surface-variant/40 rounded-xl"
                />
              </div>
            </div>

            {/* Conversation List */}
            <div className="flex-1 overflow-y-auto">
              {filteredConversations.length === 0 ? (
                <div className="text-center py-12 text-on-surface-variant">
                  <span className="material-symbols-outlined text-5xl mb-4 block opacity-30">chat</span>
                  <p className="text-body-md">No conversations</p>
                </div>
              ) : (
                filteredConversations.map(conv => (
                  <button
                    key={conv.threadId}
                    onClick={() => handleSelectThread(conv.threadId)}
                    className={`w-full p-3 text-left transition-all duration-200 ${
                      selectedThread === conv.threadId
                        ? 'bg-primary/10 border-r-2 border-primary'
                        : 'hover:bg-white/[0.05]'
                    }`}
                  >
                    <div className="flex items-center gap-3">
                      <div className={`w-10 h-10 rounded-full flex items-center justify-center flex-shrink-0 ${
                        selectedThread === conv.threadId
                          ? 'bg-primary/20 border border-primary/30'
                          : 'bg-surface-variant border border-white/10'
                      }`}>
                        <span className={`text-sm font-bold ${
                          selectedThread === conv.threadId ? 'text-primary' : 'text-on-surface-variant'
                        }`}>
                          {conv.contactName.charAt(0).toUpperCase()}
                        </span>
                      </div>
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center justify-between">
                          <h3 className="text-label-md text-on-surface font-semibold truncate">
                            {conv.contactName}
                          </h3>
                          <span className="text-[11px] text-on-surface-variant">
                            {formatTime(conv.lastMessageTime)}
                          </span>
                        </div>
                        <p className="text-label-sm text-on-surface-variant truncate font-normal">
                          {conv.lastMessage}
                        </p>
                      </div>
                      {conv.unreadCount > 0 && (
                        <span className="w-5 h-5 bg-primary text-on-primary text-[10px] font-bold rounded-full flex items-center justify-center shadow-[0_0_8px_rgba(16,185,129,0.4)]">
                          {conv.unreadCount}
                        </span>
                      )}
                    </div>
                  </button>
                ))
              )}
            </div>
          </div>

          {/* Messages Area */}
          <div className="flex-1 flex flex-col">
            {selectedConversation ? (
              <>
                {/* Header */}
                <div className="p-4 border-b border-white/[0.08] flex items-center gap-3">
                  <div className="w-9 h-9 rounded-full bg-primary/10 border border-primary/20 flex items-center justify-center">
                    <span className="text-sm font-bold text-primary">
                      {selectedConversation.contactName.charAt(0).toUpperCase()}
                    </span>
                  </div>
                  <div>
                    <h2 className="text-label-md text-on-surface font-semibold">
                      {selectedConversation.contactName}
                    </h2>
                    <p className="text-label-sm text-on-surface-variant">
                      {selectedConversation.address}
                    </p>
                  </div>
                </div>

                {/* Messages */}
                <div className="flex-1 overflow-y-auto p-4 space-y-3">
                  {messages.map(message => (
                    <div
                      key={message.id}
                      className={`flex ${message.type === 'outgoing' ? 'justify-end' : 'justify-start'}`}
                    >
                      <div
                        className={`max-w-[70%] px-4 py-2.5 ${
                          message.type === 'outgoing'
                            ? 'bg-primary-container text-white rounded-2xl rounded-br-md'
                            : 'bg-white/[0.08] border border-white/[0.08] text-on-surface rounded-2xl rounded-bl-md'
                        }`}
                      >
                        <p className="whitespace-pre-wrap break-words text-body-md">{message.body}</p>
                        <p className={`text-[10px] mt-1 ${
                          message.type === 'outgoing'
                            ? 'text-white/60'
                            : 'text-on-surface-variant'
                        }`}>
                          {formatTime(message.date)}
                        </p>
                      </div>
                    </div>
                  ))}
                  <div ref={messagesEndRef} />
                </div>

                {/* Input */}
                <div className="p-4 border-t border-white/[0.08]">
                  <div className="flex gap-3">
                    <input
                      type="text"
                      value={newMessage}
                      onChange={(e) => setNewMessage(e.target.value)}
                      onKeyDown={(e) => e.key === 'Enter' && handleSendMessage()}
                      placeholder="Type a message..."
                      className="flex-1 px-5 py-2.5 glass-input rounded-full text-body-md placeholder:text-on-surface-variant/40"
                    />
                    <button
                      onClick={handleSendMessage}
                      disabled={!newMessage.trim()}
                      className="w-10 h-10 btn-primary rounded-full flex items-center justify-center disabled:opacity-30"
                    >
                      <span className="material-symbols-outlined text-lg">send</span>
                    </button>
                  </div>
                </div>
              </>
            ) : (
              <div className="flex-1 flex items-center justify-center text-on-surface-variant">
                <div className="text-center">
                  <span className="material-symbols-outlined text-6xl mb-4 block opacity-20">chat</span>
                  <p className="text-body-lg">Select a conversation to view messages</p>
                </div>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

export default SMS;
