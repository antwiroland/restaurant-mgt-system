import { useState } from 'react';
import {
  View, Text, TextInput, TouchableOpacity, StyleSheet, ActivityIndicator, ScrollView, KeyboardAvoidingView, Platform,
} from 'react-native';
import { router } from 'expo-router';
import { requestPasswordReset, resetPassword } from '../../src/api/auth';
import { extractResetToken } from '../../src/features/auth/auth';
import { colors } from '../../src/theme';

type Step = 'request' | 'reset' | 'done';

export default function ForgotPasswordScreen() {
  const [step, setStep] = useState<Step>('request');
  const [identifier, setIdentifier] = useState('');
  const [link, setLink] = useState('');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState('');
  const [info, setInfo] = useState('');
  const [loading, setLoading] = useState(false);

  async function handleRequest() {
    if (!identifier.trim()) {
      setError('Enter the phone number or email on your account.');
      return;
    }
    setLoading(true);
    setError('');
    try {
      await requestPasswordReset(identifier.trim());
      setInfo('If an account matches, we sent a reset link by SMS or email. Open it on this phone, or paste it below.');
      setStep('reset');
    } catch (e: any) {
      setError(e?.response?.data?.message ?? 'Could not send a reset link. Please try again.');
    } finally {
      setLoading(false);
    }
  }

  async function handleReset() {
    const token = extractResetToken(link);
    if (!token) {
      setError('Paste the reset link from the message you received.');
      return;
    }
    if (password.length < 8) {
      setError('Use at least 8 characters for your new password.');
      return;
    }
    if (password !== confirm) {
      setError('The passwords do not match.');
      return;
    }
    setLoading(true);
    setError('');
    try {
      await resetPassword(token, password);
      setStep('done');
    } catch (e: any) {
      setError(e?.response?.data?.message ?? 'This link is invalid or has expired. Request a new one.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <KeyboardAvoidingView style={{ flex: 1 }} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
      <ScrollView contentContainerStyle={styles.container} keyboardShouldPersistTaps="handled">
        <Text style={styles.title}>Reset password</Text>

        {step === 'request' && (
          <>
            <Text style={styles.subtitle}>Enter the phone number or email on your account and we will send you a reset link.</Text>
            <TextInput
              style={styles.input}
              placeholder="Phone or email"
              value={identifier}
              onChangeText={setIdentifier}
              autoCapitalize="none"
              autoComplete="username"
              accessibilityLabel="Phone or email"
            />
            <TouchableOpacity style={styles.button} onPress={handleRequest} disabled={loading} accessibilityRole="button">
              {loading ? <ActivityIndicator color={colors.white} /> : <Text style={styles.buttonText}>Send reset link</Text>}
            </TouchableOpacity>
            <TouchableOpacity style={styles.link} onPress={() => { setError(''); setStep('reset'); }}>
              <Text style={styles.linkText}>I already have a reset link</Text>
            </TouchableOpacity>
          </>
        )}

        {step === 'reset' && (
          <>
            {info ? <Text style={styles.info} accessibilityLiveRegion="polite">{info}</Text> : null}
            <TextInput
              style={styles.input}
              placeholder="Paste the reset link"
              value={link}
              onChangeText={setLink}
              autoCapitalize="none"
              autoCorrect={false}
              accessibilityLabel="Reset link"
            />
            <TextInput
              style={styles.input}
              placeholder="New password (at least 8 characters)"
              value={password}
              onChangeText={setPassword}
              secureTextEntry
              autoComplete="new-password"
              accessibilityLabel="New password"
            />
            <TextInput
              style={styles.input}
              placeholder="Confirm new password"
              value={confirm}
              onChangeText={setConfirm}
              secureTextEntry
              autoComplete="new-password"
              accessibilityLabel="Confirm new password"
            />
            <TouchableOpacity style={styles.button} onPress={handleReset} disabled={loading} accessibilityRole="button">
              {loading ? <ActivityIndicator color={colors.white} /> : <Text style={styles.buttonText}>Set new password</Text>}
            </TouchableOpacity>
            <TouchableOpacity style={styles.link} onPress={() => { setError(''); setStep('request'); }}>
              <Text style={styles.linkText}>Send a new link</Text>
            </TouchableOpacity>
          </>
        )}

        {step === 'done' && (
          <>
            <Text style={styles.info} accessibilityLiveRegion="polite">
              Your password has been changed and you have been signed out on other devices.
            </Text>
            <TouchableOpacity style={styles.button} onPress={() => router.replace('/(auth)/login')} accessibilityRole="button">
              <Text style={styles.buttonText}>Sign in</Text>
            </TouchableOpacity>
          </>
        )}

        {error ? <Text style={styles.error} accessibilityLiveRegion="polite">{error}</Text> : null}

        {step !== 'done' && (
          <TouchableOpacity style={styles.link} onPress={() => router.back()}>
            <Text style={styles.backText}>Back to sign in</Text>
          </TouchableOpacity>
        )}
      </ScrollView>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  container: { flexGrow: 1, justifyContent: 'center', padding: 24, backgroundColor: colors.white },
  title: { fontSize: 28, fontWeight: '700', textAlign: 'center', marginBottom: 8, color: colors.ink },
  subtitle: { fontSize: 15, textAlign: 'center', color: colors.inkMuted, marginBottom: 24 },
  info: { fontSize: 14, color: colors.success, backgroundColor: colors.successSubtle, borderRadius: 8, padding: 12, marginBottom: 16 },
  input: { borderWidth: 1, borderColor: colors.lineStrong, borderRadius: 8, padding: 14, marginBottom: 16, fontSize: 16 },
  button: { backgroundColor: colors.brand, borderRadius: 8, padding: 16, alignItems: 'center', marginBottom: 12, minHeight: 48 },
  buttonText: { color: colors.white, fontSize: 16, fontWeight: '600' },
  link: { alignItems: 'center', paddingVertical: 10, minHeight: 44 },
  linkText: { color: colors.brand, fontSize: 14 },
  backText: { color: colors.inkMuted, fontSize: 14, textDecorationLine: 'underline' },
  error: { color: colors.dangerOn, fontSize: 14, textAlign: 'center', marginTop: 4 },
});
