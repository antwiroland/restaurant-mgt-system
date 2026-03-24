package com.restaurantmanager.core.phase8.offer;

public class BuyXGetYOfferService {
    public int freeItemsFor(int purchasedQuantity, int buyQty, int getQty) {
        if (buyQty <= 0 || getQty <= 0 || purchasedQuantity < buyQty) {
            return 0;
        }
        return (purchasedQuantity / buyQty) * getQty;
    }
}
