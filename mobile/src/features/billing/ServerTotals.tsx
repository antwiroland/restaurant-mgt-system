import { Text, View } from 'react-native';
import type { CartQuote } from '../../api/quote';

/** Breakdown rows from a server quote: offers, promo, points, delivery, service charge, tax lines and tip. */
export function ServerTotals({ quote, rowStyle, labelStyle, valueStyle, discountColor }: {
  quote: CartQuote;
  rowStyle: object;
  labelStyle: object;
  valueStyle: object;
  discountColor: string;
}) {
  const discount = [labelStyle, { color: discountColor }];
  const discountValue = [valueStyle, { color: discountColor }];
  return (
    <>
      {quote.appliedOffers.map((offer) => (
        <View key={offer.offerId} style={rowStyle}>
          <Text style={discount}>Offer: {offer.name}</Text>
          <Text style={discountValue}>-GHS {offer.discount.toFixed(2)}</Text>
        </View>
      ))}
      {quote.promoDiscount > 0 ? (
        <View style={rowStyle}>
          <Text style={discount}>Promo {quote.promoCode ?? ''}</Text>
          <Text style={discountValue}>-GHS {quote.promoDiscount.toFixed(2)}</Text>
        </View>
      ) : null}
      {quote.loyaltyDiscount > 0 ? (
        <View style={rowStyle}>
          <Text style={discount}>Loyalty points</Text>
          <Text style={discountValue}>-GHS {quote.loyaltyDiscount.toFixed(2)}</Text>
        </View>
      ) : null}
      {quote.deliveryFee > 0 ? (
        <View style={rowStyle}>
          <Text style={labelStyle}>Delivery fee</Text>
          <Text style={valueStyle}>GHS {quote.deliveryFee.toFixed(2)}</Text>
        </View>
      ) : null}
      {quote.serviceCharge > 0 ? (
        <View style={rowStyle}>
          <Text style={labelStyle}>Service charge</Text>
          <Text style={valueStyle}>GHS {quote.serviceCharge.toFixed(2)}</Text>
        </View>
      ) : null}
      {quote.taxLines.map((tax) => (
        <View key={tax.code} style={rowStyle}>
          <Text style={labelStyle}>{tax.name}{quote.taxInclusive ? ' (incl.)' : ''}</Text>
          <Text style={valueStyle}>GHS {tax.amount.toFixed(2)}</Text>
        </View>
      ))}
      {quote.tipAmount > 0 ? (
        <View style={rowStyle}>
          <Text style={labelStyle}>Tip</Text>
          <Text style={valueStyle}>GHS {quote.tipAmount.toFixed(2)}</Text>
        </View>
      ) : null}
    </>
  );
}
