import java.util.*;

/**
 * =====================================================================
 *  WasteClassifier — Robust Keyword Classification Engine
 *  Java 21 | Smart Segregation System
 *
 *  Classification Pipeline:
 *  1. Null / empty guard
 *  2. ASSET FILTER  — rejects living things, large assets, vehicles
 *  3. DISCARD MODIFIER check — "broken/crushed/empty/scrap" prefix boost
 *  4. KEYWORD LOOKUP via HashSet buckets (O(1) average)
 *  5. STATIC FALLBACK — ambiguous items → Residual (never throws)
 * =====================================================================
 */
public class WasteClassifier {

    // TIER 1 — NON-WASTE ASSET FILTER
    private static final Set<String> ASSET_KEYWORDS = Set.of(
        "person","human","man","woman","child","baby","people",
        "dog","cat","bird","fish","rabbit","hamster","parrot",
        "cow","horse","sheep","pig","goat","chicken",
        "live animal","pet","wildlife",
        "car","sports car","vehicle","truck","bus","motorcycle",
        "bicycle","bike","scooter","boat","airplane","helicopter","train","tractor",
        "sofa","couch","office chair","bed","mattress","wardrobe",
        "refrigerator","washing machine","dishwasher","oven","air conditioner",
        "houseplant","tree","shrub","live plant","potted plant",
        "building","wall","door","window","fence","gate","road","bridge",
        "wallet","passport","jewelry","gold","silver","diamond","watch","ring"
    );

    // TIER 2 — DISCARD MODIFIERS
    private static final Set<String> DISCARD_MODIFIERS = Set.of(
        "broken","crushed","empty","used","old","expired","damaged","cracked",
        "worn","torn","dirty","stained","scrap","waste","dead","dried",
        "rotting","rusted","leaking","burnt","melted","shredded","discarded",
        "leftover","spoiled","rotten","peeled","squeezed"
    );

    // LIVING BEINGS — always NON-WASTE regardless of modifiers
    private static final Set<String> LIVING_BEINGS = Set.of(
        "person","human","man","woman","child","baby","people",
        "dog","cat","bird","fish","rabbit","hamster","parrot",
        "cow","horse","sheep","pig","goat","chicken",
        "live animal","pet","wildlife"
    );

    // TIER 3 — CATEGORY BUCKETS
    private static final Set<String> ORGANIC = Set.of(
        "apple","banana","orange","mango","pear","grape","pineapple","strawberry",
        "lemon","lime","cherry","peach","plum","peel","skin","core","seed","pit",
        "husk","shell","vegetable","broccoli","carrot","potato","onion","garlic",
        "tomato","cucumber","spinach","lettuce","cabbage","corn","rice","bread",
        "pasta","noodle","flour","grain","cereal","food","meal","leftovers",
        "scraps","food waste","kitchen waste","pizza","sandwich","burger","hotdog",
        "cake","donut","coffee","grounds","tea","teabag","tea bag","egg","eggshell",
        "egg shell","grass","clipping","lawn","leaf","leaves","branch","twig","bark",
        "compost","mulch","manure","hay","plant","flower","petal","stem","root",
        "seaweed","algae","wood chip","sawdust"
    );

    private static final Set<String> RECYCLABLE = Set.of(
        "plastic","bottle","pet bottle","plastic bag","polythene","wrapper",
        "container","tub","tray","bucket","crate","glass","jar","vase",
        "glass bottle","wine glass","tumbler","can","tin","aluminium","aluminum",
        "steel","copper","iron","metal","foil","wire","wire mesh","scrap metal","paper","cardboard",
        "carton","newspaper","magazine","notebook","book","envelope","box",
        "packaging","tissue box","toilet roll","paper bag","fabric","cloth",
        "textile","rubber","tyre","tire"
    );

    private static final Set<String> HAZARDOUS = Set.of(
        "battery","batteries","lithium","alkaline","cell battery","rechargeable",
        "syringe","needle","medical","medicine","pill","tablet","capsule",
        "prescription","bandage","gauze","glove","surgical","clinical","specimen",
        "chemical","acid","bleach","solvent","thinner","paint","spray","aerosol",
        "pesticide","herbicide","fertilizer","insecticide","weedkiller","rat poison",
        "disinfectant","cleaning agent","detergent chemical","oil","motor oil",
        "petrol","diesel","fuel","grease","lubricant","brake fluid","bulb",
        "tube light","fluorescent","cfl","mercury","neon","radioactive","asbestos",
        "lead","toxic","poison","explosive","flammable","corrosive"
    );

    private static final Set<String> EWASTE = Set.of(
        "phone","mobile","smartphone","iphone","android","laptop","computer",
        "desktop","tablet","ipad","monitor","screen","keyboard","mouse","printer",
        "scanner","webcam","hard drive","ssd","ram","cpu","cable","charger","wire",
        "cord","adapter","plug","socket","extension","power bank","remote","tv",
        "television","speaker","headphone","earphone","microphone","radio","camera",
        "microwave","toaster","blender","mixer","iron","hair dryer","shaver",
        "trimmer","vacuum","circuit","pcb","chip","semiconductor","capacitor",
        "resistor","transistor","motherboard","board","smartwatch","drone",
        "router","modem","switch","projector","calculator","digital"
    );

    private static final Set<String> RESIDUAL = Set.of(
        "tissue","napkin","sanitary","diaper","nappy","pad","tampon","cotton",
        "cotton swab","qtip","styrofoam","polystyrene","foam","ceramic","pottery",
        "china","clay","toothbrush","toothpaste tube","razor","pen","marker",
        "pencil","straw","cigarette","butt","ash","dust","dirt","chewing gum",
        "gum","mixed waste","general waste","landfill"
    );

    /**
     * Classifies a waste item hint into one of 6 outcomes.
     * Returns "NON-WASTE_ASSET" for living things or functional assets.
     * Falls back to "Residual" for ambiguous/unknown items.
     * Never returns null, never throws.
     */
    public static String classifyWaste(String itemHint) {

        // Guard: null / blank
        if (itemHint == null || itemHint.isBlank()) return "Residual";

        String input  = itemHint.toLowerCase().trim();
        String[] tokens = input.split("[\\s,;./\\-]+");
        Set<String> tokenSet = new HashSet<>(Arrays.asList(tokens));

        // ── STEP 1: Living beings → always NON-WASTE_ASSET ────────
        if (tokenSet.stream().anyMatch(LIVING_BEINGS::contains)
                || LIVING_BEINGS.contains(input)) {
            return "NON-WASTE_ASSET";
        }

        // ── STEP 2: Non-living assets — block UNLESS discard modifier present
        boolean hasDiscard = tokenSet.stream().anyMatch(DISCARD_MODIFIERS::contains);
        boolean isAsset    = tokenSet.stream().anyMatch(ASSET_KEYWORDS::contains)
                             || ASSET_KEYWORDS.contains(input);

        if (isAsset && !hasDiscard) return "NON-WASTE_ASSET";

        // ── STEP 3: Full-phrase match against category buckets ─────
        if (matchesAny(input, HAZARDOUS))   return "Hazardous";
        if (matchesAny(input, ORGANIC))     return "Organic";
        if (matchesAny(input, RECYCLABLE))  return "Recyclable";
        if (matchesAny(input, EWASTE))      return "E-Waste";
        if (matchesAny(input, RESIDUAL))    return "Residual";

        // ── STEP 4: Token-level match ──────────────────────────────
        for (String token : tokens) {
            if (HAZARDOUS.contains(token))   return "Hazardous";
            if (ORGANIC.contains(token))     return "Organic";
            if (RECYCLABLE.contains(token))  return "Recyclable";
            if (EWASTE.contains(token))      return "E-Waste";
            if (RESIDUAL.contains(token))    return "Residual";
        }

        // ── STEP 5: Substring scan for partial matches ─────────────
        if (containsAny(input,"batter","acid","toxic","poison","chemical",
                "syringe","mercury","flammable","radioactive","pestici"))
            return "Hazardous";
        if (containsAny(input,"circuit","chip","electron","gadget","pcb",
                "cable","charger","device","digital","tech"))
            return "E-Waste";
        if (containsAny(input,"peel","scrap food","compost","fruit","vegetabl",
                "leaf","grass","organic","biodegr","plant waste"))
            return "Organic";
        if (containsAny(input,"plastic","paper","glass","metal ","cans",
                "tin ","cardboard","recycl","alum","foil"))
            return "Recyclable";

        // ── STEP 6: STATIC FALLBACK ────────────────────────────────
        return "Residual";
    }

    private static boolean matchesAny(String input, Set<String> keywords) {
        if (keywords.contains(input)) return true;
        for (String kw : keywords) if (input.contains(kw)) return true;
        return false;
    }

    private static boolean containsAny(String input, String... subs) {
        for (String s : subs) if (input.contains(s)) return true;
        return false;
    }

    public static void main(String[] args) {
        Object[][] tests = {
            {"banana peel",           "Organic"},
            {"broken battery",        "Hazardous"},
            {"crushed aluminium can", "Recyclable"},
            {"old laptop",            "E-Waste"},
            {"used tissue",           "Residual"},
            {"dog",                   "NON-WASTE_ASSET"},
            {"sports car",            "NON-WASTE_ASSET"},
            {"dead fish",             "NON-WASTE_ASSET"},
            {"houseplant",            "NON-WASTE_ASSET"},
            {"broken sofa",           "Residual"},
            {"backpack",              "Residual"},
            {"scrap copper wire",     "Recyclable"},
            {"empty glass bottle",    "Recyclable"},
            {"motor oil",             "Hazardous"},
            {"grass clippings",       "Organic"},
            {"circuit board",         "E-Waste"},
            {"plastic bottle",        "Recyclable"},
            {"egg shell",             "Organic"},
            {"fluorescent bulb",      "Hazardous"},
            {"charger cable",         "E-Waste"},
            {null,                    "Residual"},
            {"",                      "Residual"},
        };

        System.out.println("\n=== WasteClassifier — Test Results ===\n");
        int pass = 0, fail = 0;
        for (Object[] t : tests) {
            String result   = classifyWaste((String) t[0]);
            String expected = (String) t[1];
            boolean ok      = result.equals(expected);
            System.out.printf("%-32s → %-20s %s%n",
                "\"" + t[0] + "\"", result,
                ok ? "✅" : "❌  expected: " + expected);
            if (ok) pass++; else fail++;
        }
        System.out.printf("\n%d / %d tests passed%n", pass, pass + fail);
    }
}
