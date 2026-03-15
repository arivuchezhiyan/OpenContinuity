import React, { useState, useEffect, useRef } from 'react';
import { useConnection } from '../contexts/ConnectionContext';
import {
  ChatBubbleLeftRightIcon,
  PaperAirplaneIcon,
  MagnifyingGlassIcon
} from '@heroicons/react/24/outline';

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
    <div className="h-full flex flex-col animate-fade-in">
      <div className="mb-4">
        <h1 className="text-2xl font-bold text-gray-900 dark:text-white">SMS</h1>
        <p className="text-gray-500 dark:text-gray-400 mt-1">
          Send and receive text messages from your PC
        </p>
      </div>

      {!isConnected ? (
        <div className="bg-yellow-50 dark:bg-yellow-900/20 border border-yellow-200 dark:border-yellow-800 rounded-xl p-6 text-center">
          <p className="text-yellow-800 dark:text-yellow-200">
            Please connect to a device first to access SMS
          </p>
        </div>
      ) : (
        <div className="flex-1 flex bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 overflow-hidden">
          {/* Conversations List */}
          <div className="w-80 border-r border-gray-200 dark:border-gray-700 flex flex-col">
            {/* Search */}
            <div className="p-3 border-b border-gray-200 dark:border-gray-700">
              <div className="relative">
                <MagnifyingGlassIcon className="w-5 h-5 absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
                <input
                  type="text"
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  placeholder="Search conversations..."
                  className="w-full pl-10 pr-4 py-2 bg-gray-100 dark:bg-gray-700 rounded-lg text-gray-900 dark:text-white placeholder-gray-400 outline-none focus:ring-2 focus:ring-primary-500"
                />
              </div>
            </div>

            {/* Conversation List */}
            <div className="flex-1 overflow-y-auto">
              {filteredConversations.length === 0 ? (
                <div className="text-center py-12 text-gray-400">
                  <ChatBubbleLeftRightIcon className="w-12 h-12 mx-auto mb-4" />
                  <p>No conversations</p>
                </div>
              ) : (
                filteredConversations.map(conv => (
                  <button
                    key={conv.threadId}
                    onClick={() => handleSelectThread(conv.threadId)}
                    className={`w-full p-3 text-left hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors ${
                      selectedThread === conv.threadId
                        ? 'bg-primary-50 dark:bg-primary-900/30'
                        : ''
                    }`}
                  >
                    <div className="flex items-center gap-3">
                      <div className="w-10 h-10 bg-primary-100 dark:bg-primary-900 rounded-full flex items-center justify-center flex-shrink-0">
                        <span className="text-primary-600 dark:text-primary-400 font-medium">
                          {conv.contactName.charAt(0).toUpperCase()}
                        </span>
                      </div>
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center justify-between">
                          <h3 className="font-medium text-gray-900 dark:text-white truncate">
                            {conv.contactName}
                          </h3>
                          <span className="text-xs text-gray-400">
                            {formatTime(conv.lastMessageTime)}
                          </span>
                        </div>
                        <p className="text-sm text-gray-500 dark:text-gray-400 truncate">
                          {conv.lastMessage}
                        </p>
                      </div>
                      {conv.unreadCount > 0 && (
                        <span className="w-5 h-5 bg-primary-500 text-white text-xs rounded-full flex items-center justify-center">
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
                <div className="p-4 border-b border-gray-200 dark:border-gray-700">
                  <h2 className="font-semibold text-gray-900 dark:text-white">
                    {selectedConversation.contactName}
                  </h2>
                  <p className="text-sm text-gray-500 dark:text-gray-400">
                    {selectedConversation.address}
                  </p>
                </div>

                {/* Messages */}
                <div className="flex-1 overflow-y-auto p-4 space-y-3">
                  {messages.map(message => (
                    <div
                      key={message.id}
                      className={`flex ${message.type === 'outgoing' ? 'justify-end' : 'justify-start'}`}
                    >
                      <div
                        className={`max-w-[70%] px-4 py-2 rounded-2xl ${
                          message.type === 'outgoing'
                            ? 'bg-primary-500 text-white rounded-br-sm'
                            : 'bg-gray-100 dark:bg-gray-700 text-gray-900 dark:text-white rounded-bl-sm'
                        }`}
                      >
                        <p className="whitespace-pre-wrap break-words">{message.body}</p>
                        <p className={`text-xs mt-1 ${
                          message.type === 'outgoing'
                            ? 'text-primary-100'
                            : 'text-gray-400'
                        }`}>
                          {formatTime(message.date)}
                        </p>
                      </div>
                    </div>
                  ))}
                  <div ref={messagesEndRef} />
                </div>

                {/* Input */}
                <div className="p-4 border-t border-gray-200 dark:border-gray-700">
                  <div className="flex gap-3">
                    <input
                      type="text"
                      value={newMessage}
                      onChange={(e) => setNewMessage(e.target.value)}
                      onKeyDown={(e) => e.key === 'Enter' && handleSendMessage()}
                      placeholder="Type a message..."
                      className="flex-1 px-4 py-2 bg-gray-100 dark:bg-gray-700 rounded-full text-gray-900 dark:text-white placeholder-gray-400 outline-none focus:ring-2 focus:ring-primary-500"
                    />
                    <button
                      onClick={handleSendMessage}
                      disabled={!newMessage.trim()}
                      className="w-10 h-10 bg-primary-500 hover:bg-primary-600 disabled:bg-gray-400 text-white rounded-full flex items-center justify-center transition-colors"
                    >
                      <PaperAirplaneIcon className="w-5 h-5" />
                    </button>
                  </div>
                </div>
              </>
            ) : (
              <div className="flex-1 flex items-center justify-center text-gray-400">
                <div className="text-center">
                  <ChatBubbleLeftRightIcon className="w-16 h-16 mx-auto mb-4" />
                  <p>Select a conversation to view messages</p>
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
