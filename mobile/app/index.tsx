import { Redirect } from 'expo-router';
import { useAuthStore } from '../src/store/auth';
import { useEffect, useState } from 'react';

export default function Index() {
  const { restore, isAuthenticated, isGuest } = useAuthStore();
  const [ready, setReady] = useState(false);

  useEffect(() => {
    restore().then(() => setReady(true));
  }, []);

  if (!ready) return null;
  if (isAuthenticated || isGuest) return <Redirect href="/(tabs)/menu" />;
  return <Redirect href="/(auth)/login" />;
}
