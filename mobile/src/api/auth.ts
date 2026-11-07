import { apiClient } from './client';
import type { AuthResponse, LoginResponse } from '../types/api';
import AsyncStorage from '@react-native-async-storage/async-storage';

const SESSION_KEYS = ['accessToken', 'refreshToken', 'userId', 'userName', 'userRole'] as const;

export type OtpRequestResult = { expiresInSeconds: number; resendAfterSeconds: number };

export async function requestOtp(phone: string, purpose: 'REGISTER' | 'LOGIN'): Promise<OtpRequestResult> {
  const { data } = await apiClient.post<OtpRequestResult>('/auth/otp/request', { phone, purpose });
  return data;
}

/** Verifies a registration code and returns a short-lived phone verification token. */
export async function verifyRegistrationOtp(phone: string, code: string): Promise<string> {
  const { data } = await apiClient.post<{ verificationToken: string }>('/auth/otp/verify', { phone, code });
  return data.verificationToken;
}

export async function loginWithOtp(phone: string, code: string): Promise<LoginResponse> {
  const { data } = await apiClient.post<LoginResponse>('/auth/otp/login', { phone, code });
  await AsyncStorage.setItem('accessToken', data.accessToken);
  await AsyncStorage.setItem('refreshToken', data.refreshToken);
  return data;
}

export async function registerCustomer(
  name: string,
  phone: string,
  password: string,
  verificationToken?: string,
): Promise<AuthResponse> {
  const { data } = await apiClient.post<AuthResponse>('/auth/register', { name, phone, password, verificationToken });
  await AsyncStorage.setItem('accessToken', data.accessToken);
  await AsyncStorage.setItem('refreshToken', data.refreshToken);
  return data;
}

export async function loginCustomer(phone: string, password: string): Promise<LoginResponse> {
  const { data } = await apiClient.post<LoginResponse>('/auth/login', { phone, password });
  await AsyncStorage.setItem('accessToken', data.accessToken);
  await AsyncStorage.setItem('refreshToken', data.refreshToken);
  return data;
}

export async function logoutCustomer(): Promise<void> {
  const refreshToken = await AsyncStorage.getItem('refreshToken');
  if (refreshToken) {
    await apiClient.post('/auth/logout', { refreshToken }).catch(() => {});
  }
  await AsyncStorage.multiRemove([...SESSION_KEYS]);
}

/** Sends a single-use password reset link by email, or by SMS when the account has no email. */
export async function requestPasswordReset(emailOrPhone: string): Promise<void> {
  await apiClient.post('/auth/password/forgot', { emailOrPhone });
}

export async function resetPassword(token: string, newPassword: string): Promise<void> {
  await apiClient.post('/auth/password/reset', { token, newPassword });
}
