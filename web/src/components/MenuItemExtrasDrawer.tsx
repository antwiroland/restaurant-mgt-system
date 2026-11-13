"use client";

import { useEffect, useState } from "react";
import { Drawer } from "@/components/Drawer";
import { useStaffSession } from "@/components/SessionProvider";
import {
  getIngredients,
  getMenuItemModifiers,
  getModifierRecipe,
  getRecipe,
  removeMenuItemImage,
  setModifierRecipe,
  setRecipe,
  uploadMenuItemImage,
  type IngredientRecord,
  type MenuItemRecord,
  type MenuModifierGroupRecord,
} from "@/lib/apiClient";
import { formatMoney } from "@/lib/brand";

type RecipeDraftLine = { ingredientId: string; quantity: string };

const MAX_IMAGE_BYTES = 5 * 1024 * 1024;
const ACCEPTED = ["image/jpeg", "image/png", "image/webp"];

/** Rows of ingredient + quantity, shared by the dish recipe and option recipes. */
function RecipeLines({
  lines,
  ingredients,
  onChange,
}: {
  lines: RecipeDraftLine[];
  ingredients: IngredientRecord[];
  onChange: (lines: RecipeDraftLine[]) => void;
}) {
  const unitFor = (id: string) => ingredients.find((i) => i.id === id)?.unit ?? "";
  return (
    <>
      {lines.map((line, index) => (
        <div key={index} className="grid grid-cols-[1fr_7rem_auto] items-end gap-2">
          <label className="grid gap-1 text-sm">
            <span className={index === 0 ? "" : "sr-only"}>Ingredient</span>
            <select
              className="select"
              value={line.ingredientId}
              onChange={(e) => onChange(lines.map((l, i) => (i === index ? { ...l, ingredientId: e.target.value } : l)))}
            >
              <option value="">Select</option>
              {ingredients.map((ingredient) => <option key={ingredient.id} value={ingredient.id}>{ingredient.name}</option>)}
            </select>
          </label>
          <label className="grid gap-1 text-sm">
            <span className={index === 0 ? "" : "sr-only"}>Quantity {unitFor(line.ingredientId) ? `(${unitFor(line.ingredientId)})` : ""}</span>
            <input
              className="input"
              type="number"
              min={0}
              step="0.001"
              value={line.quantity}
              onChange={(e) => onChange(lines.map((l, i) => (i === index ? { ...l, quantity: e.target.value } : l)))}
            />
          </label>
          <button type="button" className="btn btn-ghost btn-sm" aria-label="Remove ingredient line" onClick={() => onChange(lines.filter((_, i) => i !== index))}>
            ✕
          </button>
        </div>
      ))}
    </>
  );
}

/** Ingredient cost of recipe lines at today's costs; null when an ingredient has no cost. */
function linesCost(lines: RecipeDraftLine[], ingredients: IngredientRecord[]): number | null {
  let total = 0;
  for (const line of lines) {
    if (!line.ingredientId || !(Number(line.quantity) > 0)) continue;
    const cost = ingredients.find((i) => i.id === line.ingredientId)?.costPerUnit;
    if (!cost) return null;
    total += Number(line.quantity) * Number(cost);
  }
  return total;
}

function toPayload(lines: RecipeDraftLine[]) {
  return lines
    .filter((line) => line.ingredientId && Number(line.quantity) > 0)
    .map((line) => ({ ingredientId: line.ingredientId, quantity: Number(line.quantity) }));
}

type Props = {
  item: MenuItemRecord | null;
  onClose: () => void;
  onChanged: () => void;
};

/** Photo upload and recipe (ingredients used per portion) for one menu item. */
export function MenuItemExtrasDrawer({ item, onClose, onChanged }: Props) {
  const { authenticatedFetch } = useStaffSession();
  const [imageUrl, setImageUrl] = useState<string | undefined>(undefined);
  const [file, setFile] = useState<File | null>(null);
  const [preview, setPreview] = useState<string | null>(null);
  const [ingredients, setIngredients] = useState<IngredientRecord[]>([]);
  const [lines, setLines] = useState<RecipeDraftLine[]>([]);
  const [modifierGroups, setModifierGroups] = useState<MenuModifierGroupRecord[]>([]);
  const [optionId, setOptionId] = useState("");
  const [optionLines, setOptionLines] = useState<RecipeDraftLine[]>([]);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    setError("");
    setMessage("");
    setFile(null);
    setImageUrl(item?.imageUrl);
    setOptionId("");
    setOptionLines([]);
    if (!item) return;
    Promise.all([
      authenticatedFetch((s) => getIngredients(s)),
      authenticatedFetch((s) => getRecipe(s, item.id)),
      getMenuItemModifiers(item.id).catch(() => [] as MenuModifierGroupRecord[]),
    ])
      .then(([allIngredients, recipe, groups]) => {
        setIngredients(allIngredients.filter((i) => i.active));
        setLines(recipe.map((line) => ({ ingredientId: line.ingredientId, quantity: String(Number(line.quantity)) })));
        setModifierGroups(groups);
      })
      .catch((err) => setError(err instanceof Error ? err.message : "Could not load the recipe"));
  }, [authenticatedFetch, item]);

  async function chooseOption(nextOptionId: string) {
    setOptionId(nextOptionId);
    setOptionLines([]);
    if (!nextOptionId) return;
    try {
      const recipe = await authenticatedFetch((s) => getModifierRecipe(s, nextOptionId));
      setOptionLines(recipe.map((line) => ({ ingredientId: line.ingredientId, quantity: String(Number(line.quantity)) })));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not load the option's ingredients");
    }
  }

  async function saveOptionRecipe(event: React.FormEvent) {
    event.preventDefault();
    if (!optionId) return;
    const payload = toPayload(optionLines);
    const ids = payload.map((line) => line.ingredientId);
    if (new Set(ids).size !== ids.length) {
      setError("Each ingredient can only appear once");
      return;
    }
    setBusy(true);
    setError("");
    setMessage("");
    try {
      const saved = await authenticatedFetch((s) => setModifierRecipe(s, optionId, payload));
      setOptionLines(saved.map((line) => ({ ingredientId: line.ingredientId, quantity: String(Number(line.quantity)) })));
      setMessage(saved.length === 0 ? "This option uses no extra ingredients" : "Option ingredients saved");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not save the option's ingredients");
    } finally {
      setBusy(false);
    }
  }

  useEffect(() => {
    if (!file) {
      setPreview(null);
      return;
    }
    const url = URL.createObjectURL(file);
    setPreview(url);
    return () => URL.revokeObjectURL(url);
  }, [file]);

  function chooseFile(next: File | null) {
    setError("");
    if (next && !ACCEPTED.includes(next.type)) {
      setError("Use a JPEG, PNG or WebP image");
      return;
    }
    if (next && next.size > MAX_IMAGE_BYTES) {
      setError("Images must be 5 MB or smaller");
      return;
    }
    setFile(next);
  }

  async function upload() {
    if (!item || !file) return;
    setBusy(true);
    setError("");
    setMessage("");
    try {
      const result = await authenticatedFetch((s) => uploadMenuItemImage(s, item.id, file));
      setImageUrl(result.imageUrl);
      setFile(null);
      setMessage("Photo uploaded");
      onChanged();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not upload the photo");
    } finally {
      setBusy(false);
    }
  }

  async function removeImage() {
    if (!item) return;
    setBusy(true);
    setError("");
    setMessage("");
    try {
      await authenticatedFetch((s) => removeMenuItemImage(s, item.id));
      setImageUrl(undefined);
      setMessage("Photo removed");
      onChanged();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not remove the photo");
    } finally {
      setBusy(false);
    }
  }

  async function saveRecipe(event: React.FormEvent) {
    event.preventDefault();
    if (!item) return;
    const payload = toPayload(lines);
    const ids = payload.map((line) => line.ingredientId);
    if (new Set(ids).size !== ids.length) {
      setError("Each ingredient can only appear once");
      return;
    }
    setBusy(true);
    setError("");
    setMessage("");
    try {
      const saved = await authenticatedFetch((s) => setRecipe(s, item.id, payload));
      setLines(saved.map((line) => ({ ingredientId: line.ingredientId, quantity: String(Number(line.quantity)) })));
      setMessage(saved.length === 0 ? "Recipe cleared: stock is no longer tracked for this item" : "Recipe saved");
      onChanged();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not save the recipe");
    } finally {
      setBusy(false);
    }
  }

  const shown = preview ?? imageUrl;
  const recipeCost = linesCost(lines, ingredients);
  const options = modifierGroups.flatMap((group) => group.options.map((option) => ({ id: option.id, label: `${group.name}: ${option.name}` })));

  return (
    <Drawer open={item !== null} title={item ? `${item.name}: photo and recipe` : "Menu item"} onClose={onClose}>
      {item ? (
        <div className="grid gap-5">
          <section className="grid gap-2" aria-labelledby="item-photo-heading">
            <h3 id="item-photo-heading" className="font-semibold">Photo</h3>
            {shown ? (
              // eslint-disable-next-line @next/next/no-img-element -- images come from the configured media store (local or Cloudinary)
              <img src={shown} alt={`${item.name}${preview ? " (preview)" : ""}`} className="aspect-[4/3] w-full rounded-md border border-line object-cover" />
            ) : (
              <p className="grid aspect-[4/3] w-full place-items-center rounded-md border border-dashed border-line text-sm text-ink-soft">No photo yet</p>
            )}
            <label className="grid gap-1 text-sm">
              <span>Choose an image (JPEG, PNG or WebP, up to 5 MB)</span>
              <input className="input" type="file" accept={ACCEPTED.join(",")} onChange={(e) => chooseFile(e.target.files?.[0] ?? null)} />
            </label>
            <div className="flex flex-wrap gap-2">
              <button type="button" className="btn btn-primary btn-sm" disabled={!file || busy} onClick={() => void upload()}>Upload photo</button>
              {imageUrl && !file ? (
                <button type="button" className="btn btn-danger btn-sm" disabled={busy} onClick={() => void removeImage()}>Remove photo</button>
              ) : null}
            </div>
          </section>

          <form className="grid gap-2" onSubmit={(e) => void saveRecipe(e)} aria-labelledby="item-recipe-heading">
            <h3 id="item-recipe-heading" className="font-semibold">Recipe (per portion)</h3>
            <p className="text-sm text-ink-soft">
              Each order uses these quantities from the ordering branch&apos;s stock. The dish is marked sold out at a branch when an ingredient runs out there.
            </p>
            {ingredients.length === 0 ? (
              <p className="text-sm text-ink-soft">Add ingredients on the Inventory page first.</p>
            ) : null}
            <RecipeLines lines={lines} ingredients={ingredients} onChange={setLines} />
            {lines.some((line) => line.ingredientId) ? (
              <p className="text-xs text-ink-soft">
                Ingredient cost per portion: {recipeCost === null ? "set a cost for every ingredient to see it" : formatMoney(recipeCost)}
                {recipeCost !== null && Number(item.price) > 0
                  ? ` · margin ${Math.round(((Number(item.price) - recipeCost) / Number(item.price)) * 100)}%`
                  : ""}
              </p>
            ) : null}
            <div className="flex flex-wrap gap-2">
              <button
                type="button"
                className="btn btn-secondary btn-sm"
                disabled={ingredients.length === 0}
                onClick={() => setLines([...lines, { ingredientId: "", quantity: "" }])}
              >
                Add ingredient
              </button>
              <button type="submit" className="btn btn-primary btn-sm" disabled={busy}>Save recipe</button>
            </div>
          </form>

          {options.length > 0 ? (
            <form className="grid gap-2" onSubmit={(e) => void saveOptionRecipe(e)} aria-labelledby="option-recipe-heading">
              <h3 id="option-recipe-heading" className="font-semibold">Extra ingredients for options</h3>
              <p className="text-sm text-ink-soft">
                What an option uses on top of the recipe, per portion: e.g. &quot;Large&quot; uses 0.1 kg more rice, &quot;Add chicken&quot; one more piece.
              </p>
              <label className="grid gap-1 text-sm">
                <span>Option</span>
                <select className="select" value={optionId} onChange={(e) => void chooseOption(e.target.value)}>
                  <option value="">Choose an option</option>
                  {options.map((option) => <option key={option.id} value={option.id}>{option.label}</option>)}
                </select>
              </label>
              {optionId ? (
                <>
                  <RecipeLines lines={optionLines} ingredients={ingredients} onChange={setOptionLines} />
                  <div className="flex flex-wrap gap-2">
                    <button
                      type="button"
                      className="btn btn-secondary btn-sm"
                      disabled={ingredients.length === 0}
                      onClick={() => setOptionLines([...optionLines, { ingredientId: "", quantity: "" }])}
                    >
                      Add ingredient
                    </button>
                    <button type="submit" className="btn btn-primary btn-sm" disabled={busy}>Save option ingredients</button>
                  </div>
                </>
              ) : null}
            </form>
          ) : null}

          <div aria-live="polite">
            {error ? <p className="alert alert-danger" role="alert">{error}</p> : null}
            {message ? <p className="alert alert-success">{message}</p> : null}
          </div>
        </div>
      ) : null}
    </Drawer>
  );
}
