import { useState } from 'react';
import {
  View, Text, TextInput, TouchableOpacity,
  StyleSheet, Alert, ActivityIndicator, KeyboardAvoidingView, Platform,
} from 'react-native';
import { router } from 'expo-router';
import { loginCustomer, loginWithOtp, requestOtp } from '../../src/api/auth';
import { isValidOtpCode, normalizePhone } from '../../src/features/auth/auth';
import { useAuthStore } from '../../src/store/auth';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { colors } from '../../src/theme';

export default function LoginScreen() {
  const [phone, setPhone] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [mode, setMode] = useState<'password' | 'otp'>('password');
  const [code, setCode] = useState('');
  const [codeSent, setCodeSent] = useState(false);
  const { setUser, setGuest } = useAuthStore();

  async function finishLogin(res: { user: { id: string; name: string; role: string } }) {
    await AsyncStorage.setItem('userId', res.user.id);
    await AsyncStorage.setItem('userName', res.user.name);
    await AsyncStorage.setItem('userRole', res.user.role);
    setUser(res.user.id, res.user.name, res.user.role);
    router.replace('/(tabs)/menu');
  }

  async function handleSendCode() {
    let normalized: string;
    try {
      normalized = normalizePhone(phone);
    } catch (e: any) {
      Alert.alert('Invalid phone', e.message);
      return;
    }
    setLoading(true);
    try {
      await requestOtp(normalized, 'LOGIN');
      setPhone(normalized);
      setCodeSent(true);
      Alert.alert('Code sent', 'If this number has an account, you will receive an SMS code shortly.');
    } catch (e: any) {
      Alert.alert('Could not send code', e?.response?.data?.message ?? 'Please try again');
    } finally {
      setLoading(false);
    }
  }

  async function handleOtpLogin() {
    if (!isValidOtpCode(code)) {
      Alert.alert('Invalid code', 'Enter the code from the SMS.');
      return;
    }
    setLoading(true);
    try {
      await finishLogin(await loginWithOtp(phone, code.trim()));
    } catch (e: any) {
      Alert.alert('Login failed', e?.response?.data?.message ?? 'Invalid or expired code');
    } finally {
      setLoading(false);
    }
  }

  async function handleLogin() {
    if (!phone || !password) {
      Alert.alert('Error', 'Enter phone and password');
      return;
    }
    setLoading(true);
    try {
      await finishLogin(await loginCustomer(phone, password));
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
      {mode === 'password' ? (
        <>
          <TextInput
            style={styles.input}
            placeholder="Password"
            accessibilityLabel="Password"
            value={password}
            onChangeText={setPassword}
            secureTextEntry
          />
          <TouchableOpacity style={styles.button} onPress={handleLogin} disabled={loading} accessibilityRole="button">
            {loading ? <ActivityIndicator color={colors.white} /> : <Text style={styles.buttonText}>Sign In</Text>}
          </TouchableOpacity>
        </>
      ) : codeSent ? (
        <>
          <TextInput
            style={styles.input}
            placeholder="SMS code"
            accessibilityLabel="SMS code"
            value={code}
            onChangeText={setCode}
            keyboardType="number-pad"
            maxLength={8}
            textContentType="oneTimeCode"
            autoComplete="sms-otp"
          />
          <TouchableOpacity style={styles.button} onPress={handleOtpLogin} disabled={loading} accessibilityRole="button">
            {loading ? <ActivityIndicator color={colors.white} /> : <Text style={styles.buttonText}>Verify & Sign In</Text>}
          </TouchableOpacity>
        </>
      ) : (
        <TouchableOpacity style={styles.button} onPress={handleSendCode} disabled={loading} accessibilityRole="button">
          {loading ? <ActivityIndicator color={colors.white} /> : <Text style={styles.buttonText}>Send SMS code</Text>}
        </TouchableOpacity>
      )}

      <TouchableOpacity
        style={styles.registerLink}
        onPress={() => { setMode(mode === 'password' ? 'otp' : 'password'); setCodeSent(false); setCode(''); }}
        accessibilityRole="button"
      >
        <Text style={styles.linkText}>{mode === 'password' ? 'Sign in with an SMS code instead' : 'Sign in with password instead'}</Text>
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
  container: { flex: 1, justifyContent: 'center', padding: 24, backgroundColor: colors.white },
  title: { fontSize: 28, fontWeight: '700', textAlign: 'center', marginBottom: 4, color: colors.ink },
  subtitle: { fontSize: 16, textAlign: 'center', color: colors.inkMuted, marginBottom: 32 },
  input: {
    borderWidth: 1, borderColor: colors.lineStrong, borderRadius: 8,
    padding: 14, marginBottom: 16, fontSize: 16,
  },
  button: {
    backgroundColor: colors.brand, borderRadius: 8, padding: 16,
    alignItems: 'center', marginBottom: 12, minHeight: 48,
  },
  buttonText: { color: colors.white, fontSize: 16, fontWeight: '600' },
  registerLink: { alignItems: 'center', marginBottom: 8 },
  linkText: { color: colors.brand, fontSize: 14 },
  guestButton: { alignItems: 'center', marginTop: 8 },
  guestText: { color: colors.inkMuted, fontSize: 14, textDecorationLine: 'underline' },
});
