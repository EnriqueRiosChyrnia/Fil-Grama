import { useContext } from 'react';
import { CatalogContext, type CatalogValue } from './catalogContext';

export function useCatalog(): CatalogValue {
  const ctx = useContext(CatalogContext);
  if (!ctx) throw new Error('useCatalog debe usarse dentro de <CatalogProvider>.');
  return ctx;
}
