import { useState } from 'react';
import {
  View, Text, TextInput, TouchableOpacity,
  StyleSheet, Alert, ActivityIndicator, KeyboardAvoidingView, Platform,
} from 'react-native';
import { router } from 'expo-router';
import { loginCustomer } from '../../src/api/auth';
import { useAuthStore } from '../../src/store/auth';
import AsyncStorage from '@react-native-async-storage/async-storage';

export default function LoginScreen() {
  const [phone, setPhone] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const { setUser, setGuest } = useAuthStore();

  async function handleLogin() {
    if (!phone || !password) {
      Alert.alert('Error', 'Enter phone and password');
      return;
    }
    setLoading(true);
    try {
      const res = await loginCustomer(phone, password);
      await AsyncStorage.setItem('userId', res.user.id);
      await AsyncStorage.setItem('userName', res.user.name);
      await AsyncStorage.setItem('userRole', res.user.role);
      setUser(res.user.id, res.user.name, res.user.role);
      router.replace('/(tabs)/menu');
    } catch (e: any) {
      Alert.alert('Login failed', e?.response?.data?.message ?? 'Check your credentials');
    } finally {
      setLoading(false);
    }
  }

  function handleGuest() {
    setGuest();
    router.replace('/(tabs)/menu');
  }

  return (
    <KeyboardAvoidingView
      style={styles.container}
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
    >
      <Text style={styles.title}>Restaurant Manager</Text>
      <Text style={styles.subtitle}>Welcome back</Text>

      <TextInput
        style={styles.input}
        placeholder="Phone (+233...)"
        value={phone}
        onChangeText={setPhone}
        keyboardType="phone-pad"
        autoCapitalize="none"
      />
      <TextInput
        style={styles.input}
        placeholder="Password"
        value={password}
        onChangeText={setPassword}
        secureTextEntry
      />

      <TouchableOpacity style={styles.button} onPress={handleLogin} disabled={loading}>
        {loading ? <ActivityIndicator color="#fff" /> : <Text style={styles.buttonText}>Sign In</Text>}
      </TouchableOpacity>

      <TouchableOpacity style={styles.registerLink} onPress={() => router.push('/(auth)/register')}>
        <Text style={styles.linkText}>Don't have an account? Register</Text>
      </TouchableOpacity>

      <TouchableOpacity style={styles.guestButton} onPress={handleGuest}>
        <Text style={styles.guestText}>Continue as Guest</Text>
      </TouchableOpacity>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, justifyContent: 'center', padding: 24, backgroundColor: '#fff' },
  title: { fontSize: 28, fontWeight: '700', textAlign: 'center', marginBottom: 4, color: '#111827' },
  subtitle: { fontSize: 16, textAlign: 'center', color: '#6B7280', marginBottom: 32 },
  input: {
    borderWidth: 1, borderColor: '#D1D5DB', borderRadius: 8,
    padding: 14, marginBottom: 16, fontSize: 16,
  },
  button: {
    backgroundColor: '#2563EB', borderRadius: 8, padding: 16,
    alignItems: 'center', marginBottom: 12,
  },
  buttonText: { color: '#fff', fontSize: 16, fontWeight: '600' },
  registerLink: { alignItems: 'center', marginBottom: 8 },
  linkText: { color: '#2563EB', fontSize: 14 },
  guestButton: { alignItems: 'center', marginTop: 8 },
  guestText: { color: '#6B7280', fontSize: 14, textDecorationLine: 'underline' },
});
