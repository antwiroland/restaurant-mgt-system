/** English catalog: the source of truth for keys. Placeholders use {name}. */
const en = {
  common: {
    signOut: 'Sign out',
    cancel: 'Cancel',
    retry: 'Try again',
  },
  tabs: {
    menu: 'Menu',
    orders: 'Orders',
    profile: 'Profile',
  },
  profile: {
    favourites: 'Favourites',
    restaurant: 'The restaurant',
    hours: 'Opening hours',
    call: 'Call',
    directions: 'Directions',
  },
  favourites: {
    empty: 'Tap the heart on any dish to save it here.',
    add: 'Save to favourites',
    remove: 'Remove from favourites',
  },
  tracking: {
    riderMap: 'Rider location',
  },
  rider: {
    greeting: 'Hi {name}',
    activeCount: '{count} active deliveries',
    empty: 'No deliveries assigned to you right now. New assignments arrive as notifications.',
    deliverTo: 'Deliver to',
    collect: 'Order total {amount}',
    navigate: 'Navigate',
    callCustomer: 'Call customer',
    mapHint: 'The map appears once your location is shared or the restaurant location is set.',
    sharing: 'Sharing your location with the customer',
    notSharing: 'Location sharing is paused',
    share: 'Share',
    pause: 'Pause',
    pickedUp: 'Picked up — start delivery',
    waitingKitchen: 'Waiting for the kitchen',
    markDelivered: 'Mark delivered',
    confirmDeliveredTitle: 'Delivered?',
    confirmDeliveredBody: 'Confirm the customer has received the order.',
    delivered: 'Delivered. Nice work!',
    locationDenied: 'Location permission is off. Turn it on in Settings to share your location.',
  },
};

export default en;

type Widen<T> = { [K in keyof T]: T[K] extends string ? string : Widen<T[K]> };
export type Messages = Widen<typeof en>;
