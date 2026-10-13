import React, { useEffect, useState } from 'react';
import { View, Text, ScrollView, StyleSheet, ActivityIndicator } from 'react-native';
import { apiRequest } from '../api/client';
import { useAuth } from '../context/AuthContext';
import { DEMO_AI_RUNS } from '../config';

export default function AIWorkflowsScreen() {
  const { token } = useAuth();
  const [loading, setLoading] = useState(true);
  const [items, setItems] = useState(DEMO_AI_RUNS);

  useEffect(() => {
    const load = async () => {
      try {
        const data = await apiRequest('/ai-runs', {
          headers: { Authorization: `Bearer ${token}` },
        });
        if (Array.isArray(data) && data.length) setItems(data);
      } catch (error) {
        console.warn('AI runs fetch failed:', error.message);
      } finally {
        setLoading(false);
      }
    };

    if (token) load();
  }, [token]);

  return (
    <View style={styles.container}>
      <Text style={styles.header}>AI command center</Text>

      {loading ? (
        <ActivityIndicator color="#60a5fa" size="large" style={{ marginTop: 24 }} />
      ) : (
        <ScrollView style={styles.list}>
          {items.map((item) => (
            <View key={item.id} style={styles.card}>
              <Text style={styles.name}>{item.agent_name || 'AI Agent'}</Text>
              <Text style={styles.meta}>{item.workflow_name || 'Standalone workflow'}</Text>
              <Text style={[styles.status, getStatusStyle(item.status)]}>{item.status}</Text>
            </View>
          ))}
        </ScrollView>
      )}
    </View>
  );
}

function getStatusStyle(status) {
  const palette = {
    COMPLETED: { backgroundColor: '#10b981' },
    RUNNING: { backgroundColor: '#3b82f6' },
    FAILED: { backgroundColor: '#f87171' },
  };

  return palette[status] || { backgroundColor: '#475569' };
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#07111f',
    padding: 20,
  },
  header: {
    color: '#f8fafc',
    fontSize: 28,
    fontWeight: '700',
    marginBottom: 18,
  },
  list: {
    flex: 1,
  },
  card: {
    backgroundColor: '#0f1f2f',
    borderRadius: 16,
    padding: 16,
    marginBottom: 12,
    borderWidth: 1,
    borderColor: '#1f2f3d',
  },
  name: {
    color: '#f8fafc',
    fontSize: 18,
    fontWeight: '700',
  },
  meta: {
    color: '#94a3b8',
    marginTop: 6,
    marginBottom: 12,
  },
  status: {
    color: '#f8fafc',
    alignSelf: 'flex-start',
    borderRadius: 999,
    paddingHorizontal: 10,
    paddingVertical: 5,
    fontSize: 11,
    fontWeight: '700',
    overflow: 'hidden',
  },
});
