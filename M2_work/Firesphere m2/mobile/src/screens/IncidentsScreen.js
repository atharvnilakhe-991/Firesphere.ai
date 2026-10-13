import React, { useEffect, useState } from 'react';
import { View, Text, ScrollView, StyleSheet, ActivityIndicator } from 'react-native';
import { apiRequest } from '../api/client';
import { useAuth } from '../context/AuthContext';
import { DEMO_INCIDENTS } from '../config';

export default function IncidentsScreen() {
  const { token } = useAuth();
  const [loading, setLoading] = useState(true);
  const [items, setItems] = useState(DEMO_INCIDENTS);

  useEffect(() => {
    const load = async () => {
      try {
        const data = await apiRequest('/incidents', {
          headers: { Authorization: `Bearer ${token}` },
        });
        if (Array.isArray(data) && data.length) setItems(data);
      } catch (error) {
        console.warn('Incidents fetch failed:', error.message);
      } finally {
        setLoading(false);
      }
    };

    if (token) load();
  }, [token]);

  return (
    <View style={styles.container}>
      <Text style={styles.header}>Active incidents</Text>

      {loading ? (
        <ActivityIndicator color="#60a5fa" size="large" style={{ marginTop: 24 }} />
      ) : (
        <ScrollView style={styles.list}>
          {items.map((item) => (
            <View key={item.id} style={styles.card}>
              <View style={styles.rowBetween}>
                <Text style={styles.code}>{item.incident_no}</Text>
                <Text style={[styles.badge, getBadgeStyle(item.status)]}>{item.status}</Text>
              </View>
              <Text style={styles.title}>{item.incident_type}</Text>
              <Text style={styles.meta}>Severity: {item.severity}</Text>
            </View>
          ))}
        </ScrollView>
      )}
    </View>
  );
}

function getBadgeStyle(status) {
  const palette = {
    DISPATCHED: { backgroundColor: '#1d4ed8' },
    ON_SCENE: { backgroundColor: '#f59e0b' },
    RESOLVED: { backgroundColor: '#10b981' },
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
  rowBetween: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  code: {
    color: '#f8fafc',
    fontWeight: '700',
  },
  badge: {
    color: '#f8fafc',
    borderRadius: 999,
    paddingHorizontal: 10,
    paddingVertical: 4,
    fontSize: 11,
    fontWeight: '700',
    overflow: 'hidden',
  },
  title: {
    color: '#e2e8f0',
    fontSize: 16,
    marginTop: 10,
  },
  meta: {
    color: '#94a3b8',
    marginTop: 8,
  },
});
