import { useEffect, useState } from 'react';
import {
  Text, TextInput, TouchableOpacity,
  StyleSheet, Alert, ActivityIndicator, KeyboardAvoidingView, Platform, ScrollView,
} from 'react-native';
import { router } from 'expo-router';
import { registerCustomer, requestOtp, verifyRegistrationOtp } from '../../src/api/auth';
import { isValidOtpCode, normalizePhone, resendSecondsLeft } from '../../src/features/auth/auth';
import { useAuthStore } from '../../src/store/auth';
import AsyncStorage from '@react-native-async-storage/async-storage';

type Step = 'details' | 'code';

export default function RegisterScreen() {
  const [step, setStep] = useState<Step>('details');
  const [name, setName] = useState('');
  const [phone, setPhone] = useState('');
  const [password, setPassword] = useState('');
  const [code, setCode] = useState('');
  const [requestedAt, setRequestedAt] = useState<number | null>(null);
  const [cooldown, setCooldown] = useState(60);
  const [secondsLeft, setSecondsLeft] = useState(0);
  const [loading, setLoading] = useState(false);
  const { setUser } = useAuthStore();

  useEffect(() => {
    if (requestedAt === null) return;
    const tick = () => setSecondsLeft(resendSecondsLeft(requestedAt, cooldown));
    tick();
    const timer = setInterval(tick, 1000);
    return () => clearInterval(timer);
  }, [requestedAt, cooldown]);

  async function sendCode() {
    if (!name.trim() || !phone.trim() || password.length < 6) {
      Alert.alert('Missing details', 'Enter your name, phone and a password of at least 6 characters.');
      return;
    }
    let normalized: string;
    try {
      normalized = normalizePhone(phone);
    } catch (e: any) {
      Alert.alert('Invalid phone', e.message);
      return;
    }
    setLoading(true);
    try {
      const result = await requestOtp(normalized, 'REGISTER');
      setPhone(normalized);
      setCooldown(result.resendAfterSeconds);
      setRequestedAt(Date.now());
      setStep('code');
    } catch (e: any) {
      Alert.alert('Could not send code', e?.response?.data?.message ?? 'Please try again');
    } finally {
      setLoading(false);
    }
  }

  async function verifyAndRegister() {
    if (!isValidOtpCode(code)) {
      Alert.alert('Invalid code', 'Enter the code we sent by SMS.');
      return;
    }
    setLoading(true);
    try {
      const verificationToken = await verifyRegistrationOtp(phone, code.trim());
      const res = await registerCustomer(name.trim(), phone, password, verificationToken);
      await AsyncStorage.setItem('userId', res.id);
      await AsyncStorage.setItem('userName', res.name);
      await AsyncStorage.setItem('userRole', res.role);
      setUser(res.id, res.name, res.role);
      router.replace('/(tabs)/menu');
    } catch (e: any) {
      Alert.alert('Verification failed', e?.response?.data?.message ?? 'Please try again');
    } finally {
      setLoading(false);
    }
  }

  return (
    <KeyboardAvoidingView style={{ flex: 1 }} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
      <ScrollView contentContainerStyle={styles.container} keyboardShouldPersistTaps="handled">
        <Text style={styles.title} accessibilityRole="header">Create Account</Text>

        {step === 'details' ? (
          <>
            <TextInput style={styles.input} placeholder="Full Name" value={name} onChangeText={setName} accessibilityLabel="Full name" />
            <TextInput
              style={styles.input} placeholder="Phone (+233...)" accessibilityLabel="Phone number"
              value={phone} onChangeText={setPhone} keyboardType="phone-pad" autoCapitalize="none"
            />
            <TextInput
              style={styles.input} placeholder="Password (min 6 characters)" accessibilityLabel="Password"
              value={password} onChangeText={setPassword} secureTextEntry
            />
            <TouchableOpacity style={styles.button} onPress={sendCode} disabled={loading} accessibilityRole="button">
              {loading ? <ActivityIndicator color="#fff" /> : <Text style={styles.buttonText}>Send verification code</Text>}
            </TouchableOpacity>
          </>
        ) : (
          <>
            <Text style={styles.info}>We sent a code by SMS to {phone}.</Text>
            <TextInput
              style={[styles.input, styles.codeInput]} placeholder="123456" accessibilityLabel="Verification code"
              value={code} onChangeText={setCode} keyboardType="number-pad" maxLength={8}
              textContentType="oneTimeCode" autoComplete="sms-otp"
            />
            <TouchableOpacity style={styles.button} onPress={verifyAndRegister} disabled={loading} accessibilityRole="button">
              {loading ? <ActivityIndicator color="#fff" /> : <Text style={styles.buttonText}>Verify & create account</Text>}
            </TouchableOpacity>
            <TouchableOpacity
              onPress={sendCode}
              disabled={secondsLeft > 0 || loading}
              style={styles.back}
              accessibilityRole="button"
            >
              <Text style={[styles.backText, secondsLeft > 0 && styles.disabledText]}>
                {secondsLeft > 0 ? `Resend code in ${secondsLeft}s` : 'Resend code'}
              </Text>
            </TouchableOpacity>
            <TouchableOpacity onPress={() => { setStep('details'); setCode(''); }} style={styles.back} accessibilityRole="button">
              <Text style={styles.backText}>Change phone number</Text>
            </TouchableOpacity>
          </>
        )}

        <TouchableOpacity onPress={() => router.back()} style={styles.back} accessibilityRole="link">
          <Text style={styles.backText}>Already have an account? Sign in</Text>
        </TouchableOpacity>
      </ScrollView>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  container: { flexGrow: 1, justifyContent: 'center', padding: 24, backgroundColor: '#fff' },
  title: { fontSize: 28, fontWeight: '700', textAlign: 'center', marginBottom: 32, color: '#111827' },
  info: { fontSize: 15, color: '#374151', textAlign: 'center', marginBottom: 16 },
  input: {
    borderWidth: 1, borderColor: '#D1D5DB', borderRadius: 8,
    padding: 14, marginBottom: 16, fontSize: 16,
  },
  codeInput: { fontSize: 24, letterSpacing: 8, textAlign: 'center' },
  button: {
    backgroundColor: '#2563EB', borderRadius: 8, padding: 16,
    alignItems: 'center', marginBottom: 12, minHeight: 48,
  },
  buttonText: { color: '#fff', fontSize: 16, fontWeight: '600' },
  back: { alignItems: 'center', paddingVertical: 10 },
  backText: { color: '#2563EB', fontSize: 14 },
  disabledText: { color: '#9CA3AF' },
});
