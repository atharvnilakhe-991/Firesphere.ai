import React from 'react';
import { View, Text, StyleSheet, TouchableOpacity } from 'react-native';
import { useAuth } from '../context/AuthContext';

export default function ProfileScreen() {
  const { user, logout } = useAuth();

  return (
    <View style={styles.container}>
      <Text style={styles.header}>Profile</Text>

      <View style={styles.card}>
        <Text style={styles.label}>User</Text>
        <Text style={styles.value}>{user?.firstName || 'System'} {user?.lastName || 'Administrator'}</Text>
        <Text style={styles.label}>Email</Text>
        <Text style={styles.value}>{user?.email || 'admin@firesphere.ai'}</Text>
        <Text style={styles.label}>Organization</Text>
        <Text style={styles.value}>#{user?.orgId || 1}</Text>
      </View>

      <TouchableOpacity style={styles.button} onPress={logout}>
        <Text style={styles.buttonText}>Sign out</Text>
      </TouchableOpacity>
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
  card: {
    backgroundColor: '#0f1f2f',
    borderRadius: 18,
    padding: 20,
    borderWidth: 1,
    borderColor: '#1f2f3d',
  },
  label: {
    color: '#94a3b8',
    fontSize: 12,
    marginTop: 12,
  },
  value: {
    color: '#f8fafc',
    fontSize: 18,
    fontWeight: '600',
    marginTop: 6,
  },
  button: {
    marginTop: 22,
    backgroundColor: '#ef4444',
    borderRadius: 12,
    paddingVertical: 14,
    alignItems: 'center',
  },
  buttonText: {
    color: '#fff',
    fontWeight: '700',
    fontSize: 16,
  },
});
