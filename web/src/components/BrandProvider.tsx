"use client";

import { createContext, useContext } from "react";
import { brand as envBrand, type Brand } from "@/lib/brand";

const BrandContext = createContext<Brand>(envBrand);

/** Supplies the restaurant profile (fetched on the server per request) to client components. */
export function BrandProvider({ brand, children }: { brand: Brand; children: React.ReactNode }) {
  return <BrandContext.Provider value={brand}>{children}</BrandContext.Provider>;
}

export function useBrand(): Brand {
  return useContext(BrandContext);
}
