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

    /**
     * OFX `<SIC>` merchant-category codes → category name. Card issuers populate this field with the
     * standard 4-digit MCC, so this table is what makes the first credit-card import broadly useful
     * before any per-user model exists. Codes map to a canonical leaf (or, when the MCC is generic,
     * the parent category); [ColdStartKnowledge] resolves the name against the user's own categories.
     */
    val SIC_CSV = """
        sicCode,categoryName
        5411,Groceries
        5422,Groceries
        5441,Groceries
        5451,Groceries
        5462,Groceries
        5499,Groceries
        5300,Groceries
        5811,Restaurants
        5812,Restaurants
        5813,Alcohol & Bars
        5814,Fast Food
        5921,Alcohol & Bars
        5541,Gas & Fuel
        5542,Gas & Fuel
        5172,Gas & Fuel
        5983,Gas & Fuel
        5531,Auto Maintenance
        5532,Auto Maintenance
        5533,Auto Maintenance
        7542,Auto Maintenance
        7531,Auto Repairs
        7534,Auto Repairs
        7535,Auto Repairs
        7538,Auto Repairs
        7549,Auto Repairs
        7523,Parking
        4784,Tolls
        4011,Public Transit
        4111,Public Transit
        4112,Public Transit
        4131,Public Transit
        4789,Public Transit
        4121,Rideshare/Taxi
        4511,Airfare
        4582,Airfare
        7011,Hotels
        7012,Hotels
        7512,Car Rental
        7513,Car Rental
        7519,Car Rental
        4411,Vacation Activities
        4457,Vacation Activities
        4722,Vacation Activities
        5611,Clothing
        5621,Clothing
        5631,Clothing
        5641,Clothing
        5651,Clothing
        5655,Clothing
        5661,Clothing
        5691,Clothing
        5699,Clothing
        5948,Clothing
        7296,Clothing
        5045,Electronics
        5732,Electronics
        5734,Electronics
        5946,Electronics
        5712,Household Items
        5713,Household Items
        5714,Household Items
        5719,Household Items
        5722,Household Items
        5311,Household Items
        5331,Household Items
        5399,Household Items
        5931,Household Items
        5943,Household Items
        5200,Home Improvement
        5211,Home Improvement
        5231,Home Improvement
        5251,Home Improvement
        5261,Home Improvement
        1711,Repairs & Maintenance
        1731,Repairs & Maintenance
        1750,Repairs & Maintenance
        1799,Repairs & Maintenance
        5947,Gifts Given
        5992,Gifts Given
        5942,Books
        5735,Music
        5733,Hobbies
        5945,Hobbies
        5949,Hobbies
        5970,Hobbies
        5940,Sports
        5941,Sports
        7941,Sports
        7992,Sports
        7933,Sports
        7997,Gym/Fitness
        7832,Movies & Shows
        7841,Movies & Shows
        7922,Concerts/Events
        7929,Concerts/Events
        7991,Vacation Activities
        7996,Vacation Activities
        7998,Vacation Activities
        7999,Vacation Activities
        7993,Games
        7994,Games
        4899,Cable/Streaming
        4814,Phone/Mobile
        4815,Phone/Mobile
        4812,Phone/Mobile
        4816,Internet
        4900,Utilities
        5912,Pharmacy/Prescriptions
        5122,Pharmacy/Prescriptions
        5975,Doctor/Medical
        5976,Doctor/Medical
        8011,Doctor/Medical
        8031,Doctor/Medical
        8041,Doctor/Medical
        8049,Doctor/Medical
        8050,Doctor/Medical
        8062,Doctor/Medical
        8071,Doctor/Medical
        8099,Doctor/Medical
        8021,Dentist
        8042,Vision/Eyecare
        8043,Vision/Eyecare
        7230,Haircuts
        7297,Spa/Massage
        7298,Spa/Massage
        7299,Personal Care
        5977,Cosmetics
        0742,Vet Bills
        5995,Pet Supplies
        8211,Tuition
        8220,Tuition
        8241,Tuition
        8244,Tuition
        8249,Tuition
        8299,Tuition
        8351,Childcare
        8398,Donations & Charity
        8661,Donations & Charity
        6011,ATM Fees
        6012,Bank Fees
        6051,Bank Fees
        6300,Insurance
        5960,Insurance
        5964,Shopping
        5965,Shopping
        5967,Shopping
        5969,Shopping
        5999,Shopping
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
