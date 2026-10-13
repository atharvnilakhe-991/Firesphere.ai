import React, { useEffect, useState } from 'react';
import { View, Text, ScrollView, StyleSheet, ActivityIndicator } from 'react-native';
import { apiRequest } from '../api/client';
import { useAuth } from '../context/AuthContext';
import { DEMO_ORGANIZATIONS } from '../config';

export default function OrganizationsScreen() {
  const { token } = useAuth();
  const [loading, setLoading] = useState(true);
  const [items, setItems] = useState(DEMO_ORGANIZATIONS);

  useEffect(() => {
    const load = async () => {
      try {
        const data = await apiRequest('/organizations', {
          headers: { Authorization: `Bearer ${token}` },
        });
        if (Array.isArray(data) && data.length) setItems(data);
      } catch (error) {
        console.warn('Organizations fetch failed:', error.message);
      } finally {
        setLoading(false);
      }
    };

    if (token) load();
  }, [token]);

  return (
    <View style={styles.container}>
      <Text style={styles.header}>Organizations</Text>

      {loading ? (
        <ActivityIndicator color="#60a5fa" size="large" style={{ marginTop: 24 }} />
      ) : (
        <ScrollView style={styles.list}>
          {items.map((item) => (
            <View key={item.id} style={styles.card}>
              <Text style={styles.name}>{item.name}</Text>
              <Text style={styles.meta}>{item.org_type}</Text>
              <View style={styles.rowBetween}>
                <Text style={styles.city}>{item.city}</Text>
                <Text style={[styles.status, getStatusStyle(item.status)]}>{item.status}</Text>
              </View>
            </View>
          ))}
        </ScrollView>
      )}
    </View>
  );
}

function getStatusStyle(status) {
  const palette = {
    ACTIVE: { backgroundColor: '#10b981' },
    TRIAL: { backgroundColor: '#f59e0b' },
    PENDING: { backgroundColor: '#f87171' },
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
    marginBottom: 10,
  },
  rowBetween: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  city: {
    color: '#cbd5e1',
  },
  status: {
    color: '#f8fafc',
    borderRadius: 999,
    paddingHorizontal: 10,
    paddingVertical: 4,
    fontSize: 11,
    fontWeight: '700',
    overflow: 'hidden',
  },
});
