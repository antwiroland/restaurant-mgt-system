import React from 'react';
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest';
import { act, create } from 'react-test-renderer';
import LoginScreen from '../app/(auth)/login';
import MenuScreen from '../app/(tabs)/menu';
import CheckoutScreen from '../app/checkout';
import { useConnectivity } from './hooks/useConnectivity';
import { useCartStore } from './store/cart';
import { useAuthStore } from './store/auth';
import { useOfflineStore } from './store/offline';

declare const require: any;

const rn = vi.hoisted(() => {
  const React = require('react');
  const makeComponent = (name: string) => {
    const Component = (props: Record<string, unknown>) => React.createElement(name, props, props.children);
    Component.displayName = name;
    return Component;
  };

  return {
    Alert: { alert: vi.fn() },
    TextInput: makeComponent('TextInput'),
    TouchableOpacity: makeComponent('TouchableOpacity'),
    View: makeComponent('View'),
    Text: makeComponent('Text'),
    ActivityIndicator: makeComponent('ActivityIndicator'),
    Image: makeComponent('Image'),
    Modal: makeComponent('Modal'),
    ScrollView: makeComponent('ScrollView'),
    KeyboardAvoidingView: makeComponent('KeyboardAvoidingView'),
    FlatList: (props: Record<string, unknown>) => {
      const data = (props.data as unknown[]) ?? [];
      const renderItem = props.renderItem as ((args: { item: unknown; index: number }) => React.ReactNode) | undefined;
      const items = renderItem ? data.map((item, index) => renderItem({ item, index })) : null;
      return React.createElement('FlatList', props, items, props.ListFooterComponent);
    },
    StyleSheet: {
      create: <T,>(styles: T) => styles,
      absoluteFillObject: {},
    },
    Platform: { OS: 'android' },
  };
});

const router = vi.hoisted(() => ({
  replace: vi.fn(),
  push: vi.fn(),
  back: vi.fn(),
}));

const asyncStorage = vi.hoisted(() => ({
  setItem: vi.fn(async () => undefined),
  getItem: vi.fn(async () => null),
  multiRemove: vi.fn(async () => undefined),
}));

const loginCustomer = vi.hoisted(() => vi.fn());
const fetchCategories = vi.hoisted(() => vi.fn());
const fetchMenuItemModifiers = vi.hoisted(() => vi.fn());
const createOrder = vi.hoisted(() => vi.fn());
const createPublicTableOrder = vi.hoisted(() => vi.fn());
const initiatePayment = vi.hoisted(() => vi.fn());
const scanQrToken = vi.hoisted(() => vi.fn());
const netInfoAddEventListener = vi.hoisted(() => vi.fn());

vi.mock('react-native', () => rn);

vi.mock('expo-router', () => ({
  router,
  useLocalSearchParams: () => ({ id: 'order-1', code: 'GROUP1' }),
}));

vi.mock('@react-native-async-storage/async-storage', () => ({
  default: asyncStorage,
}));

vi.mock('./api/auth', () => ({
  loginCustomer,
}));

vi.mock('./api/menu', () => ({
  fetchCategories,
  fetchMenuItemModifiers,
}));

vi.mock('./api/orders', () => ({
  createOrder,
  createPublicTableOrder,
}));

vi.mock('./api/payment', () => ({
  initiatePayment,
}));

vi.mock('./api/tables', () => ({
  scanQrToken,
}));

vi.mock('@react-native-community/netinfo', () => ({
  default: {
    addEventListener: netInfoAddEventListener,
  },
}));

function flushPromises() {
  return new Promise((resolve) => setTimeout(resolve, 0));
}

async function pressByText(root: ReturnType<typeof create>, text: string) {
  const button = root.root.findAllByType(rn.TouchableOpacity).find((node: any) => {
    return node.findAll((child: any) => typeof child.props?.children === 'string' && child.props.children === text).length > 0;
  });
  if (!button) {
    throw new Error(`Button with text "${text}" not found`);
  }
  await act(async () => {
    button.props.onPress();
    await flushPromises();
  });
}

function setInputValue(root: ReturnType<typeof create>, placeholder: string, value: string) {
  const input = root.root.findAllByType(rn.TextInput).find((node: any) => node.props.placeholder === placeholder);
  if (!input) {
    throw new Error(`Input with placeholder "${placeholder}" not found`);
  }
  act(() => {
    input.props.onChangeText(value);
  });
}

beforeEach(() => {
  vi.clearAllMocks();
  useCartStore.setState({
    lines: [],
    tableId: null,
    tableNumber: null,
    tableToken: null,
    tableStatus: null,
    promoCode: null,
    promoType: null,
    promoValue: null,
  });
  useAuthStore.setState({
    userId: null,
    name: null,
    role: null,
    isAuthenticated: false,
    isGuest: false,
  });
  useOfflineStore.setState({
    queue: [],
    isOnline: true,
  });
});

afterEach(() => {
  vi.useRealTimers();
});

describe('mobile screens', () => {
  test('login screen signs in and persists session metadata', async () => {
    loginCustomer.mockResolvedValue({
      accessToken: 'token',
      refreshToken: 'refresh',
      expiresIn: 900,
      user: { id: 'user-1', name: 'Kofi', role: 'CUSTOMER' },
    });

    let tree!: ReturnType<typeof create>;
    await act(async () => {
      tree = create(<LoginScreen />);
    });

    setInputValue(tree, 'Phone (+233...)', '+233201234567');
    setInputValue(tree, 'Password', 'secret123');

    await act(async () => {
      await pressByText(tree, 'Sign In');
    });

    expect(loginCustomer).toHaveBeenCalledWith('+233201234567', 'secret123');
    expect(asyncStorage.setItem).toHaveBeenCalledWith('userId', 'user-1');
    expect(asyncStorage.setItem).toHaveBeenCalledWith('userName', 'Kofi');
    expect(asyncStorage.setItem).toHaveBeenCalledWith('userRole', 'CUSTOMER');
    expect(router.replace).toHaveBeenCalledWith('/(tabs)/menu');
  });

  test('menu screen loads categories and adds item to cart', async () => {
    fetchCategories.mockResolvedValue([
      {
        id: 'cat-1',
        name: 'Rice',
        displayOrder: 1,
        items: [{ id: 'm1', name: 'Jollof', description: 'Smoky', price: '25.00', available: true, categoryId: 'cat-1' }],
      },
    ]);
    fetchMenuItemModifiers.mockResolvedValue([]);

    let tree!: ReturnType<typeof create>;
    await act(async () => {
      tree = create(<MenuScreen />);
      await flushPromises();
    });

    await pressByText(tree, 'Add');

    expect(fetchCategories).toHaveBeenCalled();
    expect(useCartStore.getState().lines).toHaveLength(1);
    expect(useCartStore.getState().lines[0].item.name).toBe('Jollof');
  });

  test('checkout screen queues offline order and redirects after confirmation', async () => {
    (rn.Alert.alert as ReturnType<typeof vi.fn>).mockImplementation((_: string, __: string, buttons?: Array<{ onPress?: () => void }>) => {
      buttons?.[0]?.onPress?.();
    });
    const enqueue = vi.fn(async () => undefined);
    useOfflineStore.setState({ isOnline: false, enqueue });
    useCartStore.setState({
      lines: [
        {
          key: 'm1:base',
          item: { id: 'm1', name: 'Jollof', description: '', price: '25.00', available: true, categoryId: 'cat-1' },
          quantity: 2,
          unitPrice: '25.00',
        },
      ],
    });

    let tree!: ReturnType<typeof create>;
    await act(async () => {
      tree = create(<CheckoutScreen />);
    });

    await act(async () => {
      await pressByText(tree, 'Place Order');
    });

    expect(enqueue).toHaveBeenCalledTimes(1);
    expect(rn.Alert.alert).toHaveBeenCalledWith(
      'Order queued',
      'Order saved. It will be placed when you reconnect.',
      expect.any(Array)
    );
    expect(router.replace).toHaveBeenCalledWith('/(tabs)/orders');
  });

  test('connectivity hook syncs queued orders when app is already online', async () => {
    const markSynced = vi.fn(async () => undefined);
    const markFailed = vi.fn(async () => undefined);
    const markConflict = vi.fn(async () => undefined);
    const setOnline = vi.fn();
    createOrder.mockResolvedValue({ id: 'o1' });
    netInfoAddEventListener.mockImplementation(() => () => undefined);

    useOfflineStore.setState({
      isOnline: true,
      queue: [
        {
          id: 'q1',
          action: 'CREATE_ORDER',
          payload: {
            type: 'PICKUP',
            items: [{ menuItemId: 'm1', quantity: 1 }],
          },
          createdAt: new Date().toISOString(),
          retryCount: 0,
          status: 'QUEUED',
        },
      ],
      markSynced,
      markFailed,
      markConflict,
      setOnline,
    });

    function HookHarness() {
      useConnectivity();
      return null;
    }

    await act(async () => {
      create(<HookHarness />);
      await flushPromises();
    });

    expect(createOrder).toHaveBeenCalledWith({
      type: 'PICKUP',
      items: [{ menuItemId: 'm1', quantity: 1 }],
    });
    expect(markSynced).toHaveBeenCalledWith('q1');
    expect(markFailed).not.toHaveBeenCalled();
    expect(markConflict).not.toHaveBeenCalled();
  });
});
