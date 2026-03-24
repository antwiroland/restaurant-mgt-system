export type TableStatus = "AVAILABLE" | "OCCUPIED";
export type TableItem = { id: string; number: string; status: TableStatus };

export function tableColor(status: TableStatus): string {
  return status === "AVAILABLE" ? "green" : "red";
}

export function activeOrdersForTable(tableId: string, orders: { tableId?: string; id: string }[]): string[] {
  return orders.filter((order) => order.tableId === tableId).map((order) => order.id);
}

export function applyTableStatusEvent(tables: TableItem[], tableId: string, status: TableStatus): TableItem[] {
  return tables.map((table) => (table.id === tableId ? { ...table, status } : table));
}
