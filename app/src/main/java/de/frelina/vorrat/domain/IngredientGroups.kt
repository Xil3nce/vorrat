package de.frelina.vorrat.domain

import java.text.Normalizer

/**
 * Zutatengruppe für den Rezeptabgleich (z. B. "pasta" = Nudeln). Produkte und Rezept-Zutaten werden denselben
 * Gruppen zugeordnet; so passt "Penne" im Vorrat zu "Nudeln" im Rezept.
 *
 * @param id stabile ID (englisch, wird synchronisiert und im Backup gespeichert – nie umbenennen)
 * @param label deutscher Anzeigename
 * @param basic Grundvorrat (Salz, Öl, Gewürze …): wird vorausgesetzt und nie als fehlend gewertet
 * @param nameKeywords deutsche Stichwörter für den Produktnamen
 * @param categoryKeywords englische Stichwörter für die Open-Food-Facts-Kategorie
 */
class IngredientGroup internal constructor(
    val id: String,
    val label: String,
    val basic: Boolean,
    internal val nameKeywords: List<String>,
    internal val categoryKeywords: List<String>,
)

/** Feste Liste der Zutatengruppen. */
object IngredientGroups {

    private fun g(id: String, label: String, names: String, cats: String = "", basic: Boolean = false) =
        IngredientGroup(
            id = id,
            label = label,
            basic = basic,
            nameKeywords = names.split(',').map(String::trim).filter(String::isNotEmpty),
            categoryKeywords = cats.split(',').map(String::trim).filter(String::isNotEmpty),
        )

    /**
     * Stichwort-Syntax: Normalisierung macht Groß-/Kleinschreibung und Umlaute egal (ä = ae, ß = ss, é = e).
     * Ein Stichwort trifft, wenn es am Wortanfang oder am Wortende steht (Wortende = Kopf eines deutschen
     * Kompositums: "Vollkornspaghetti"); Stichwörter unter 4 Buchstaben und solche mit "=" davor müssen
     * ein ganzes Wort sein.
     */
    val all: List<IngredientGroup> = listOf(
        g("pasta", "Nudeln",
            "nudel, nudeln, spaghetti, penne, fusilli, farfalle, tagliatelle, makkaroni, maccheroni, rigatoni, " +
                "spirelli, lasagne, pasta, linguine, fettuccine, bandnudel, hörnchennudel, " +
                "eiernudel, cannelloni, orecchiette, spiralen, gabelspaghetti, suppennudel, hartweizengrieß",
            "pasta, spaghetti, penne, macaroni, fusilli, tagliatelle, lasagne, egg noodle, egg pasta"),
        g("rice", "Reis", "reis, basmati, risotto, jasmin, parboiled, milchreis, langkornreis, rundkornreis",
            "rice"),
        g("potatoes", "Kartoffeln",
            "kartoffel, kartoffeln, pommes, kroketten, rösti, kartoffelpüree, püree, bratkartoffel, kloßteig",
            "potato, potatoes, french fries, fries, mashed potato"),
        g("minced_meat", "Hackfleisch", "hackfleisch, hack, gehacktes, =mett, hackbällchen, hackepeter, =tatar, hacksteak",
            "minced meat, ground beef, minced, ground meat, mince"),
        g("chicken", "Hähnchen / Geflügel",
            "hähnchen, hühnchen, huhn, hühner, chicken, geflügel, pute, putenbrust, puten, hähnchenschnitzel, " +
                "putenschnitzel, hähnchenbrust, entenbrust, ente",
            "chicken, poultry, turkey, duck"),
        g("pork", "Schwein", "schwein, schweinefleisch, schnitzel, kotelett, nacken, schweinebraten, bratwurstfleisch, " +
            "gyros, lende, schweinefilet", "pork"),
        g("beef", "Rind", "=rind, rinder, rindfleisch, rindsteak, gulasch, rouladen, rumpsteak, entrecote, hüftsteak, " +
            "suppenfleisch", "beef"),
        g("cream", "Sahne", "sahne, schlagsahne, kochsahne, cremefine, creme fine, rahm, schlagobers, kaffeesahne, " +
            "küchensahne", "cream, whipping cream, fresh cream, creams"),
        g("sour_cream", "Schmand / saure Sahne", "schmand, saure sahne, sauerrahm, saurer rahm, smetana",
            "sour cream, smetana"),
        g("creme_fraiche", "Crème fraîche", "creme fraiche, cremefraiche, fraiche", "creme fraiche, creme fraiche"),
        g("milk", "Milch", "milch, vollmilch, frischmilch, buttermilch, h-milch, haltbare milch, trinkmilch, " +
            "fettarme milch", "milk, milks, whole milk"),
        g("cheese", "Käse",
            "käse, gouda, edamer, emmentaler, cheddar, bergkäse, schnittkäse, reibekäse, raclette, gruyère, " +
                "tilsiter, camembert, =brie, appenzeller, maasdam, butterkäse, ziegenkäse, hartkäse, scheibenkäse, " +
                "gratinkäse, pizzakäse, burgerkäse, leerdammer",
            "cheese, cheeses, cheddar, gouda, emmental, camembert"),
        g("cream_cheese", "Frischkäse", "frischkäse, doppelrahmfrischkäse, philadelphia, hüttenkäse, körniger frischkäse",
            "cream cheese, cottage cheese, spreadable cheese"),
        g("mozzarella", "Mozzarella", "mozzarella, büffelmozzarella, burrata", "mozzarella, burrata"),
        g("parmesan", "Parmesan", "parmesan, grana padano, pecorino, parmigiano", "parmesan, parmigiano, grana padano, pecorino"),
        g("feta", "Feta / Hirtenkäse", "feta, hirtenkäse, schafskäse, salakis", "feta"),
        g("eggs", "Eier", "ei, eier, vollei, freilandeier, bio-eier, eigelb, eiweiß", "egg, eggs"),
        g("butter", "Butter", "butter, süßrahmbutter, butterschmalz, margarine, streichfett", "butter, butters, margarine"),
        g("yogurt", "Joghurt", "joghurt, jogurt, yoghurt, naturjoghurt, griechischer joghurt",
            "yogurt, yoghurt, yogurts, yoghurts, yogourt"),
        g("quark", "Quark / Skyr", "quark, magerquark, speisequark, skyr", "quark, skyr, fromage frais"),
        g("canned_tomatoes", "Dosentomaten",
            "dosentomaten, gehackte tomaten, passierte tomaten, stückige tomaten, tomatenstücke, pizzatomaten, " +
                "passata, geschälte tomaten, tomaten passiert",
            "canned tomato, chopped tomato, passata, peeled tomato, tomato puree"),
        g("tomato_paste", "Tomatenmark", "tomatenmark, tomatenpüree, tomatenkonzentrat", "tomato paste, tomato concentrate"),
        g("tomato_sauce", "Tomatensoße", "tomatensauce, tomatensoße, pastasauce, nudelsauce, arrabbiata, pizzasauce",
            "tomato sauce, pasta sauce, pizza sauce"),
        g("tomatoes", "Tomaten", "tomate, tomaten, cherrytomaten, kirschtomaten, rispentomaten, datteltomaten, " +
            "romatomaten", "tomato, tomatoes, cherry tomato"),
        g("bell_pepper", "Paprika", "paprika, spitzpaprika, snackpaprika", "bell pepper, paprika, sweet pepper"),
        g("onions", "Zwiebeln", "zwiebel, zwiebeln, schalotte, frühlingszwiebel, lauchzwiebel, röstzwiebeln",
            "onion, onions, shallot"),
        g("garlic", "Knoblauch", "knoblauch", "garlic"),
        g("zucchini", "Zucchini", "zucchini, zucchetti", "zucchini, courgette"),
        g("eggplant", "Aubergine", "aubergine", "eggplant, aubergine"),
        g("pumpkin", "Kürbis", "kürbis, hokkaido, butternut", "pumpkin, squash"),
        g("carrots", "Karotten / Möhren", "karotte, karotten, möhre, möhren, mohrrübe", "carrot, carrots"),
        g("mushrooms", "Pilze", "pilz, pilze, champignon, champignons, steinpilz, pfifferling, kräuterseitling, shiitake",
            "mushroom, mushrooms"),
        g("spinach", "Spinat", "spinat, blattspinat, rahmspinat, babyspinat", "spinach"),
        g("broccoli", "Brokkoli", "brokkoli, broccoli", "broccoli"),
        g("cauliflower", "Blumenkohl", "blumenkohl, romanesco", "cauliflower"),
        g("cabbage", "Kohl", "kohl, weißkohl, rotkohl, wirsing, spitzkohl, kohlrabi, grünkohl, sauerkraut, chinakohl",
            "cabbage, kale, sauerkraut"),
        g("bacon", "Speck / Bacon", "speck, bacon, schinkenspeck, frühstücksspeck, pancetta, bauchspeck, würfelspeck, " +
            "räucherspeck, speckwürfel", "bacon, pancetta, lardons"),
        g("ham", "Schinken", "schinken, kochschinken, nussschinken, lachsschinken, parmaschinken, kasseler, " +
            "schinkenwürfel", "ham, hams, cooked ham"),
        g("sausage", "Wurst", "wurst, würstchen, bratwurst, bockwurst, salami, fleischwurst, chorizo, mettwurst, " +
            "currywurst, wiener würstchen, cabanossi, kabanos", "sausage, sausages, salami, chorizo"),
        g("fish", "Fisch", "fisch, fischfilet, seelachs, kabeljau, forelle, pangasius, dorade, scholle, hering, " +
            "matjes, rotbarsch, makrele, zander, garnelen, shrimps", "fish, cod, herring, trout, mackerel, shrimp, prawn"),
        g("tuna", "Thunfisch", "thunfisch, tunfisch", "tuna"),
        g("salmon", "Lachs", "lachs, räucherlachs, lachsfilet", "salmon"),
        g("fish_sticks", "Fischstäbchen", "fischstäbchen, backfisch, fischburger, fish sticks",
            "fish stick, fish finger, breaded fish"),
        g("flour", "Mehl / Stärke", "mehl, weizenmehl, dinkelmehl, vollkornmehl, kartoffelmehl, kartoffelstärke, " +
            "speisestärke, maisstärke", "flour, flours, starch"),
        g("sugar", "Zucker", "zucker, puderzucker, vanillezucker, rohrzucker, brauner zucker", "sugar, sugars"),
        g("bread", "Brot / Brötchen", "brot, brötchen, baguette, ciabatta, vollkornbrot, mischbrot, fladenbrot, " +
            "semmel, weckle, laugenbrezel, knäckebrot, baguettebrötchen", "bread, breads, baguette, bread roll"),
        g("toast", "Toast", "toast, toastbrot, sandwichbrot, sandwichtoast", "toast, sandwich bread"),
        g("tortilla", "Tortilla / Wraps", "tortilla, tortillas, wrap, wraps, tacoschalen, fladen", "tortilla, wraps, flatbread"),
        g("puff_pastry", "Blätterteig", "blätterteig, strudelteig, plunderteig, filoteig, yufka", "puff pastr, filo"),
        g("pizza_dough", "Pizzateig", "pizzateig, pizzaboden, hefeteig, flammkuchenteig, flammkuchen, pizza teig",
            "pizza dough, pizza base, pizza crust"),
        g("gnocchi", "Gnocchi", "gnocchi", "gnocchi"),
        g("filled_pasta", "Tortellini / Ravioli", "tortellini, tortelloni, ravioli, cappelletti, agnolotti, girasoli, " +
            "mezzelune, gefüllte pasta, gefüllte nudel", "stuffed pasta, filled pasta, tortellini, ravioli"),
        g("noodles_asian", "Asia-Nudeln", "glasnudel, reisnudel, mienudel, mie nudel, ramen, udon, soba, " +
            "instantnudel, asia nudel, wok nudel, chinesische nudel", "rice noodle, instant noodle, ramen, udon, soba, noodle"),
        g("spaetzle", "Spätzle", "spätzle, spatzle, knöpfle", "spaetzle, spatzle"),
        g("dumplings", "Klöße / Knödel", "knödel, =kloß, klöße, kartoffelklöße, semmelknödel, serviettenknödel, " +
            "kartoffelknödel, maultasche, pierogi", "dumpling, knodel"),
        g("lentils", "Linsen", "linse, linsen, belugalinsen", "lentil, lentils"),
        g("chickpeas", "Kichererbsen", "kichererbse, kichererbsen, hummus", "chickpea, chickpeas, hummus"),
        g("beans", "Bohnen", "bohne, bohnen, kidneybohnen, schnittbohnen, baked beans", "bean, beans, kidney"),
        g("corn", "Mais", "mais, zuckermais, maiskolben, maiskörner", "corn, sweet corn, maize"),
        g("peas", "Erbsen", "erbse, erbsen, zuckerschoten, tiefkühlerbsen", "pea, peas, mange tout"),
        g("coconut_milk", "Kokosmilch", "kokosmilch, kokosnussmilch, kokoscreme, coconut milk", "coconut milk, coconut cream"),
        g("stock", "Brühe", "brühe, bouillon, gemüsebrühe, hühnerbrühe, rinderbrühe, =fond, brühwürfel, gemüsefond, " +
            "instantbrühe, suppenwürfel", "broth, bouillon, stock cube, stock"),
        g("apples", "Äpfel", "apfel, äpfel, apfelmus", "apple, apples, applesauce"),
        g("bananas", "Bananen", "banane, bananen", "banana, bananas"),
        g("lemons", "Zitronen / Limetten", "zitrone, zitronen, limette, limetten", "lemon, lemons, lime, limes"),
        g("oats", "Haferflocken / Müsli", "haferflocken, haferflocke, hafer, müsli, porridge, kernige haferflocken",
            "oat, oats, oatmeal, rolled oats, muesli, granola"),
        g("tofu", "Tofu", "tofu, räuchertofu, seidentofu, tempeh", "tofu, tempeh"),
        g("leek", "Lauch", "lauch, porree, stangenlauch", "leek, leeks"),
        g("cucumber", "Gurke", "gurke, gurken, salatgurke", "cucumber, cucumbers"),
        g("salad", "Salat", "salat, eisbergsalat, kopfsalat, feldsalat, rucola, blattsalat, romanasalat, " +
            "salatmischung, endivie, römersalat", "salad, lettuce, arugula, rocket, mixed salad"),
        g("berries", "Beeren", "beere, beeren, erdbeere, himbeere, heidelbeere, blaubeere, brombeere, johannisbeere, " +
            "waldbeeren, tk beeren, cranberry", "berry, berries, strawberry, raspberry, blueberry"),
        g("chocolate", "Schokolade", "schokolade, schoki, kuvertüre, kakao, zartbitter, nutella, nuss-nougat-creme, " +
            "schokostreusel, schokotropfen", "chocolate, cocoa, hazelnut spread"),
        g("honey", "Honig / Sirup", "honig, ahornsirup, sirup, agavendicksaft", "honey, honeys, syrup, maple syrup"),
        g("pesto", "Pesto", "pesto", "pesto, pestos"),
        g("soy_sauce", "Sojasoße", "sojasoße, sojasauce, soja sauce, tamari, teriyaki", "soy sauce, soya sauce, tamari, teriyaki"),
        g("curry_paste", "Currypaste", "currypaste, curry paste, thai curry, rote currypaste, grüne currypaste",
            "curry paste, thai curry"),
        g("frozen_vegetables", "TK-Gemüse", "tk gemüse, tiefkühlgemüse, gemüsemischung, rahmgemüse, pfannengemüse, " +
            "wokgemüse, buttergemüse, gemüse", "frozen vegetable, vegetable mix, mixed vegetables"),
        g("herbs", "Kräuter", "kräuter, petersilie, schnittlauch, basilikum, dill, koriander, minze, bärlauch, " +
            "salbei, kerbel, rosmarin, thymian", "herb, herbs, basil, parsley, chive, coriander, mint"),
        // Grundvorrat – wird vorausgesetzt und nie als fehlend gewertet.
        g("spices", "Salz, Pfeffer & Gewürze", "salz, pfeffer, gewürz, gewürze, paprikapulver, currypulver, curry, " +
            "zimt, muskat, oregano, kreuzkümmel, kümmel, chili, lorbeer, vanille, backpulver, natron, hefe, senf, " +
            "ketchup, mayonnaise, mayo, zitronensaft, gewürzmischung, pfefferkörner", "spice, spices, salt, pepper, condiment, ketchup, mustard, mayonnaise", basic = true),
        g("oil", "Öl & Essig", "=öl, olivenöl, sonnenblumenöl, rapsöl, speiseöl, pflanzenöl, sesamöl, kokosöl, " +
            "essig, balsamico, apfelessig, weißweinessig", "oil, oils, olive oil, vinegar", basic = true),
        g("water", "Wasser", "wasser, mineralwasser, sprudel, leitungswasser", "water, waters", basic = true),
    )

    private val byId: Map<String, IngredientGroup> = all.associateBy { it.id }

    fun byId(id: String?): IngredientGroup? = id?.let(byId::get)

    fun isKnown(id: String?): Boolean = id != null && id in byId

    fun label(id: String?): String? = byId(id)?.label

    /** Grundvorrat (Salz, Öl, Gewürze …) wird nie als fehlend gewertet. */
    fun isBasic(id: String?): Boolean = byId(id)?.basic == true

    /** Alle Gruppen alphabetisch nach Anzeigename (für Auswahllisten). */
    val sortedByLabel: List<IngredientGroup> by lazy {
        all.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }

    /**
     * Treffer, die ausdrücklich keiner Gruppe zugeordnet werden sollen (Fehltreffer-Schutz). Gewinnt eines von ihnen
     * den Wettbewerb um das beste Stichwort, ist das Ergebnis "unklar" (null).
     */
    internal val excluded: List<String> = listOf(
        "erdnussbutter", "erdnußbutter", "butterkeks", "peanut butter", "buttermilchbrot",
        "hafermilch", "sojamilch", "mandelmilch", "reismilch", "haferdrink", "sojadrink", "mandeldrink",
        "reisdrink", "kokosdrink", "pflanzendrink", "kaffeebohne", "vanillebohne", "kakaobohne",
        "popcorn", "cornflakes", "corn flakes", "cornichon", "honigmelone", "ice cream", "eiscreme", "speiseeis",
        "kartoffelchips", "chips", "paprikapulver", "paprikachips", "tomatenketchup",
        "suppe", "saft", "schorle", "juice", "marmelade", "konfitüre", "konfiture", "jam", "dressing",
        "essiggurke", "gewürzgurke", "senfgurke", "pickle", "gherkin", "dillgurke", "fondue", "fondant",
        "milchschnitte", "kinderschokolade", "milchschokolade", "gummibär", "kaugummi",
    )
}

/** Ordnet Produktnamen und Open-Food-Facts-Kategorien einer Zutatengruppe zu. */
object IngredientMatcher {

    private class Keyword(val text: String, val exact: Boolean, val groupId: String?)

    private val nameIndex: List<Keyword> by lazy { buildIndex(excludedAlso = true) { it.nameKeywords } }
    private val categoryIndex: List<Keyword> by lazy { buildIndex(excludedAlso = true) { it.categoryKeywords } }

    private fun buildIndex(excludedAlso: Boolean, pick: (IngredientGroup) -> List<String>): List<Keyword> {
        val list = mutableListOf<Keyword>()
        IngredientGroups.all.forEach { group ->
            pick(group).forEach { raw ->
                val exactMarker = raw.startsWith("=")
                val text = normalize(raw.removePrefix("="))
                if (text.isNotEmpty()) list += Keyword(text, exactMarker || text.length < MIN_AFFIX_LENGTH, group.id)
            }
        }
        if (excludedAlso) {
            IngredientGroups.excluded.forEach { raw ->
                val text = normalize(raw)
                list += Keyword(text, text.length < MIN_AFFIX_LENGTH, null)
            }
        }
        return list
    }

    /**
     * Kleinschreibung, Umlaute (ä = ae, ö = oe, ü = ue, ß = ss), Akzente entfernt, Sonderzeichen → Leerzeichen.
     */
    fun normalize(text: String): String {
        val replaced = buildString(text.length) {
            for (ch in text.lowercase()) {
                when (ch) {
                    'ä' -> append("ae")
                    'ö' -> append("oe")
                    'ü' -> append("ue")
                    'ß' -> append("ss")
                    else -> append(ch)
                }
            }
        }
        val stripped = Normalizer.normalize(replaced, Normalizer.Form.NFD).filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }
        return stripped.map { if (it.isLetterOrDigit()) it else ' ' }.joinToString("").trim().replace(Regex(" +"), " ")
    }

    /**
     * Gruppen-ID für ein Produkt: erst die (spezifischste) OFF-Kategorie, dann der Produktname.
     * Bei mehreren Treffern gewinnt das längste Stichwort (Kopf eines Kompositums zählt mehr),
     * z. B. "Sahnejoghurt" → Joghurt, nicht Sahne. null, wenn unklar.
     */
    fun match(name: String, category: String?): String? {
        if (!category.isNullOrBlank()) {
            matchText(category, categoryIndex)?.let { return it.groupId }
        }
        return matchText(name, nameIndex)?.groupId
    }

    /** Wie [match] nur für einen freien Namen (z. B. Zutat eines eigenen Rezepts). */
    fun matchName(name: String): String? = match(name, null)

    /** Bester Treffer; ein ausgeschlossenes Stichwort als Gewinner ergibt null (kein Fallback auf Schwächere). */
    private fun matchText(raw: String, index: List<Keyword>): Keyword? {
        val text = " " + normalize(raw) + " "
        if (text.isBlank()) return null
        var best: Keyword? = null
        var bestScore = 0
        for (k in index) {
            val score = score(text, k)
            if (score > bestScore) {
                best = k
                bestScore = score
            }
        }
        return best?.takeIf { it.groupId != null }
    }

    private fun score(text: String, k: Keyword): Int {
        var from = 0
        var best = 0
        while (true) {
            val i = text.indexOf(k.text, from)
            if (i < 0) return best
            val start = text[i - 1] == ' '
            val end = text[i + k.text.length] == ' '
            val ok = if (k.exact) start && end else start || end
            if (ok) best = maxOf(best, k.text.length + if (end) HEAD_BONUS else 0)
            from = i + 1
        }
    }

    private const val MIN_AFFIX_LENGTH = 4
    private const val HEAD_BONUS = 3
}
