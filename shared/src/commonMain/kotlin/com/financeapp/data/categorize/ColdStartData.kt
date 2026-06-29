package com.financeapp.data.categorize

/**
 * The bundled cold-start lexicons, embedded as CSV text.
 *
 * They live as string constants (not classpath resource files) because the shared module has no
 * resource-loading infrastructure for non-Compose assets, and embedding keeps the data fully
 * available to pure `commonMain` code and to `commonTest` with no `expect`/`actual` plumbing and no
 * new dependency. The format is still plain `key,categoryName` CSV parsed by [ColdStartKnowledge].
 *
 * Every `categoryName` must match a leaf (or category) name in
 * `com.financeapp.data.seed.DefaultCategories`; names the user doesn't have are skipped at runtime.
 */
internal object ColdStartData {

    /** OFX SIC / merchant-category codes → category name. */
    val SIC_CSV = """
        sicCode,categoryName
        5812,Restaurants
        5814,Fast Food
        5813,Alcohol & Bars
        5411,Groceries
        5422,Groceries
        5451,Groceries
        5462,Groceries
        5499,Groceries
        5541,Gas & Fuel
        5542,Gas & Fuel
        5172,Gas & Fuel
        4111,Public Transit
        4131,Public Transit
        4121,Rideshare/Taxi
        4511,Airfare
        7011,Hotels
        7512,Car Rental
        7523,Parking
        7216,Clothing
        5651,Clothing
        5621,Clothing
        5732,Electronics
        5734,Electronics
        5942,Books
        5943,Books
        5912,Pharmacy/Prescriptions
        8011,Doctor/Medical
        8021,Dentist
        8042,Vision/Eyecare
        8062,Doctor/Medical
        7230,Haircuts
        7298,Personal Care
        7997,Gym/Fitness
        7991,Gym/Fitness
        7832,Movies & Shows
        7841,Movies & Shows
        4899,Cable/Streaming
        4814,Phone/Mobile
        4816,Internet
        0742,Vet Bills
        5995,Pet Supplies
        5947,Gifts Given
        5331,Household Items
        5311,Household Items
        5200,Home Improvement
        5211,Home Improvement
        5251,Home Improvement
    """.trimIndent()

    /** Merchant-name keywords (whole word/phrase, case-insensitive) → category name. */
    val MERCHANT_KEYWORDS_CSV = """
        keyword,categoryName
        starbucks,Coffee Shops
        dunkin,Coffee Shops
        peets,Coffee Shops
        philz,Coffee Shops
        mcdonald,Fast Food
        burger king,Fast Food
        wendys,Fast Food
        taco bell,Fast Food
        chipotle,Fast Food
        subway,Fast Food
        chick-fil-a,Fast Food
        popeyes,Fast Food
        uber eats,Fast Food
        doordash,Fast Food
        grubhub,Fast Food
        shell,Gas & Fuel
        chevron,Gas & Fuel
        exxon,Gas & Fuel
        mobil,Gas & Fuel
        texaco,Gas & Fuel
        arco,Gas & Fuel
        valero,Gas & Fuel
        costco,Groceries
        safeway,Groceries
        kroger,Groceries
        trader joe,Groceries
        whole foods,Groceries
        aldi,Groceries
        publix,Groceries
        wegmans,Groceries
        walmart,Household Items
        target,Household Items
        home depot,Home Improvement
        lowes,Home Improvement
        ace hardware,Home Improvement
        best buy,Electronics
        netflix,Cable/Streaming
        hulu,Cable/Streaming
        disney plus,Cable/Streaming
        hbo max,Cable/Streaming
        youtube,Cable/Streaming
        amazon prime,Cable/Streaming
        spotify,Music
        pandora,Music
        comcast,Internet
        xfinity,Internet
        verizon,Phone/Mobile
        att wireless,Phone/Mobile
        uber,Rideshare/Taxi
        lyft,Rideshare/Taxi
        delta air,Airfare
        southwest air,Airfare
        united airlines,Airfare
        american airlines,Airfare
        marriott,Hotels
        hilton,Hotels
        hyatt,Hotels
        airbnb,Hotels
        cvs,Pharmacy/Prescriptions
        walgreens,Pharmacy/Prescriptions
        rite aid,Pharmacy/Prescriptions
        planet fitness,Gym/Fitness
        la fitness,Gym/Fitness
        equinox,Gym/Fitness
        petco,Pet Supplies
        petsmart,Pet Supplies
        chewy,Pet Food
        steam games,Games
        playstation,Games
        xbox,Games
        nintendo,Games
        gamestop,Games
        audible,Books
        amc,Movies & Shows
        regal cinemas,Movies & Shows
        cinemark,Movies & Shows
        amazon,Shopping
    """.trimIndent()
}
