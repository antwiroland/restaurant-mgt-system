import { useState } from 'react';
import {
  View, Text, TextInput, TouchableOpacity,
  StyleSheet, Alert, ActivityIndicator, KeyboardAvoidingView, Platform, ScrollView,
} from 'react-native';
import { router } from 'expo-router';
import { registerCustomer } from '../../src/api/auth';
import { useAuthStore } from '../../src/store/auth';
import AsyncStorage from '@react-native-async-storage/async-storage';

export default function RegisterScreen() {
  const [name, setName] = useState('');
  const [phone, setPhone] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const { setUser } = useAuthStore();

  async function handleRegister() {
    if (!name || !phone || !password) {
      Alert.alert('Error', 'All fields are required');
      return;
    }
    setLoading(true);
    try {
      const res = await registerCustomer(name, phone, password);
      await AsyncStorage.setItem('userId', res.id);
      await AsyncStorage.setItem('userName', res.name);
      await AsyncStorage.setItem('userRole', res.role);
      setUser(res.id, res.name, res.role);
      router.replace('/(tabs)/menu');
    } catch (e: any) {
      Alert.alert('Registration failed', e?.response?.data?.message ?? 'Please try again');
    } finally {
      setLoading(false);
    }
  }

  return (
    <KeyboardAvoidingView
      style={{ flex: 1 }}
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
    >
      <ScrollView contentContainerStyle={styles.container}>
        <Text style={styles.title}>Create Account</Text>

        <TextInput style={styles.input} placeholder="Full Name" value={name} onChangeText={setName} />
        <TextInput
          style={styles.input} placeholder="Phone (+233...)"
          value={phone} onChangeText={setPhone} keyboardType="phone-pad" autoCapitalize="none"
        />
        <TextInput
          style={styles.input} placeholder="Password"
          value={password} onChangeText={setPassword} secureTextEntry
        />

        <TouchableOpacity style={styles.button} onPress={handleRegister} disabled={loading}>
          {loading ? <ActivityIndicator color="#fff" /> : <Text style={styles.buttonText}>Register</Text>}
        </TouchableOpacity>

        <TouchableOpacity onPress={() => router.back()} style={styles.back}>
          <Text style={styles.backText}>Already have an account? Sign in</Text>
        </TouchableOpacity>
      </ScrollView>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  container: { flexGrow: 1, justifyContent: 'center', padding: 24, backgroundColor: '#fff' },
  title: { fontSize: 28, fontWeight: '700', textAlign: 'center', marginBottom: 32, color: '#111827' },
  input: {
    borderWidth: 1, borderColor: '#D1D5DB', borderRadius: 8,
    padding: 14, marginBottom: 16, fontSize: 16,
  },
  button: {
    backgroundColor: '#2563EB', borderRadius: 8, padding: 16,
    alignItems: 'center', marginBottom: 12,
  },
  buttonText: { color: '#fff', fontSize: 16, fontWeight: '600' },
  back: { alignItems: 'center' },
  backText: { color: '#2563EB', fontSize: 14 },
});
