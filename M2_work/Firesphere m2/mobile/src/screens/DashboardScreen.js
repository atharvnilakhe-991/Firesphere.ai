import React, { useEffect, useState } from 'react';
import { View, Text, ScrollView, StyleSheet, ActivityIndicator } from 'react-native';
import { apiRequest } from '../api/client';
import { useAuth } from '../context/AuthContext';
import { DEMO_STATS, DEMO_REPORTS } from '../config';

export default function DashboardScreen() {
  const { token } = useAuth();
  const [loading, setLoading] = useState(true);
  const [stats, setStats] = useState(DEMO_STATS);

  useEffect(() => {
    const loadData = async () => {
      try {
        const data = await apiRequest('/dashboard', {
          headers: { Authorization: `Bearer ${token}` },
        });
        if (data?.stats) {
          setStats(data.stats);
        }
      } catch (error) {
        console.warn('Dashboard failed:', error.message);
      } finally {
        setLoading(false);
      }
    };

    if (token) {
      loadData();
    }
  }, [token]);

  const statCards = [
    { label: 'Organizations', value: stats.organizations, color: '#60a5fa' },
    { label: 'Users', value: stats.users, color: '#a78bfa' },
    { label: 'Incidents', value: stats.incidents, color: '#f87171' },
    { label: 'Inspections', value: stats.inspections, color: '#34d399' },
  ];

  return (
    <View style={styles.container}>
      <Text style={styles.header}>Operations overview</Text>

      {loading ? (
        <ActivityIndicator color="#60a5fa" size="large" style={{ marginTop: 20 }} />
      ) : (
        <ScrollView showsVerticalScrollIndicator={false}>
          <View style={styles.grid}>
            {statCards.map((item) => (
              <View key={item.label} style={[styles.card, { borderColor: item.color }]}> 
                <Text style={styles.cardLabel}>{item.label}</Text>
                <Text style={[styles.cardValue, { color: item.color }]}>{item.value}</Text>
              </View>
            ))}
          </View>

          <View style={styles.panel}>
            <Text style={styles.panelTitle}>Performance summary</Text>
            {DEMO_REPORTS.map((report) => (
              <View key={report.id} style={styles.reportRow}>
                <View>
                  <Text style={styles.reportTitle}>{report.title}</Text>
                  <Text style={styles.reportValue}>{report.value}</Text>
                </View>
                <Text style={styles.reportTrend}>{report.trend}</Text>
              </View>
            ))}
          </View>
        </ScrollView>
      )}
    </View>
  );
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
  grid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    justifyContent: 'space-between',
  },
  card: {
    width: '48%',
    backgroundColor: '#0f1f2f',
    borderRadius: 16,
    padding: 16,
    borderWidth: 1,
    marginBottom: 14,
  },
  cardLabel: {
    color: '#cbd5e1',
    fontSize: 12,
    marginBottom: 10,
  },
  cardValue: {
    fontSize: 26,
    fontWeight: '700',
  },
  panel: {
    backgroundColor: '#0f1f2f',
    borderRadius: 18,
    padding: 18,
    marginTop: 8,
    borderWidth: 1,
    borderColor: '#1f2f3d',
  },
  panelTitle: {
    color: '#f8fafc',
    fontSize: 18,
    fontWeight: '700',
    marginBottom: 12,
  },
  reportRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingVertical: 12,
    borderBottomWidth: 1,
    borderBottomColor: '#1f2f3d',
  },
  reportTitle: {
    color: '#cbd5e1',
    fontSize: 14,
  },
  reportValue: {
    color: '#f8fafc',
    fontSize: 20,
    fontWeight: '700',
    marginTop: 4,
  },
  reportTrend: {
    color: '#34d399',
    fontSize: 14,
    fontWeight: '700',
  },
});
