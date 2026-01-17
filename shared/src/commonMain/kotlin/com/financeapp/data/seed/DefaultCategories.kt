package com.financeapp.data.seed

import com.financeapp.domain.model.CategoryType

/**
 * Default categories for a personal finance app.
 * Based on common categories from Quicken, Mint, and other personal finance tools.
 */
object DefaultCategories {

    data class CategoryDef(
        val name: String,
        val type: CategoryType,
        val icon: String? = null,
        val color: String? = null,
        val subcategories: List<SubcategoryDef> = emptyList()
    )

    data class SubcategoryDef(
        val name: String,
        val icon: String? = null,
        val color: String? = null
    )

    val incomeCategories = listOf(
        CategoryDef(
            name = "Salary & Wages",
            type = CategoryType.INCOME,
            icon = "work"
        ),
        CategoryDef(
            name = "Bonus",
            type = CategoryType.INCOME,
            icon = "celebration"
        ),
        CategoryDef(
            name = "Interest Income",
            type = CategoryType.INCOME,
            icon = "savings"
        ),
        CategoryDef(
            name = "Dividend Income",
            type = CategoryType.INCOME,
            icon = "trending_up"
        ),
        CategoryDef(
            name = "Investment Gains",
            type = CategoryType.INCOME,
            icon = "show_chart"
        ),
        CategoryDef(
            name = "Rental Income",
            type = CategoryType.INCOME,
            icon = "home"
        ),
        CategoryDef(
            name = "Business Income",
            type = CategoryType.INCOME,
            icon = "business"
        ),
        CategoryDef(
            name = "Gifts Received",
            type = CategoryType.INCOME,
            icon = "card_giftcard"
        ),
        CategoryDef(
            name = "Tax Refund",
            type = CategoryType.INCOME,
            icon = "account_balance"
        ),
        CategoryDef(
            name = "Other Income",
            type = CategoryType.INCOME,
            icon = "attach_money"
        )
    )

    val expenseCategories = listOf(
        CategoryDef(
            name = "Housing",
            type = CategoryType.EXPENSE,
            icon = "home",
            subcategories = listOf(
                SubcategoryDef("Mortgage/Rent"),
                SubcategoryDef("Property Tax"),
                SubcategoryDef("Home Insurance"),
                SubcategoryDef("HOA Fees"),
                SubcategoryDef("Repairs & Maintenance"),
                SubcategoryDef("Home Improvement")
            )
        ),
        CategoryDef(
            name = "Utilities",
            type = CategoryType.EXPENSE,
            icon = "bolt",
            subcategories = listOf(
                SubcategoryDef("Electric"),
                SubcategoryDef("Gas"),
                SubcategoryDef("Water & Sewer"),
                SubcategoryDef("Trash"),
                SubcategoryDef("Internet"),
                SubcategoryDef("Phone/Mobile"),
                SubcategoryDef("Cable/Streaming")
            )
        ),
        CategoryDef(
            name = "Transportation",
            type = CategoryType.EXPENSE,
            icon = "directions_car",
            subcategories = listOf(
                SubcategoryDef("Gas & Fuel"),
                SubcategoryDef("Auto Insurance"),
                SubcategoryDef("Auto Maintenance"),
                SubcategoryDef("Auto Repairs"),
                SubcategoryDef("Parking"),
                SubcategoryDef("Tolls"),
                SubcategoryDef("Public Transit"),
                SubcategoryDef("Rideshare/Taxi"),
                SubcategoryDef("Auto Registration")
            )
        ),
        CategoryDef(
            name = "Food & Dining",
            type = CategoryType.EXPENSE,
            icon = "restaurant",
            subcategories = listOf(
                SubcategoryDef("Groceries"),
                SubcategoryDef("Restaurants"),
                SubcategoryDef("Coffee Shops"),
                SubcategoryDef("Fast Food"),
                SubcategoryDef("Alcohol & Bars")
            )
        ),
        CategoryDef(
            name = "Healthcare",
            type = CategoryType.EXPENSE,
            icon = "local_hospital",
            subcategories = listOf(
                SubcategoryDef("Health Insurance"),
                SubcategoryDef("Doctor/Medical"),
                SubcategoryDef("Dentist"),
                SubcategoryDef("Vision/Eyecare"),
                SubcategoryDef("Pharmacy/Prescriptions"),
                SubcategoryDef("Gym/Fitness")
            )
        ),
        CategoryDef(
            name = "Insurance",
            type = CategoryType.EXPENSE,
            icon = "security",
            subcategories = listOf(
                SubcategoryDef("Life Insurance"),
                SubcategoryDef("Disability Insurance"),
                SubcategoryDef("Umbrella Insurance")
            )
        ),
        CategoryDef(
            name = "Personal Care",
            type = CategoryType.EXPENSE,
            icon = "spa",
            subcategories = listOf(
                SubcategoryDef("Haircuts"),
                SubcategoryDef("Cosmetics"),
                SubcategoryDef("Spa/Massage")
            )
        ),
        CategoryDef(
            name = "Shopping",
            type = CategoryType.EXPENSE,
            icon = "shopping_bag",
            subcategories = listOf(
                SubcategoryDef("Clothing"),
                SubcategoryDef("Electronics"),
                SubcategoryDef("Household Items"),
                SubcategoryDef("Gifts Given")
            )
        ),
        CategoryDef(
            name = "Entertainment",
            type = CategoryType.EXPENSE,
            icon = "movie",
            subcategories = listOf(
                SubcategoryDef("Movies & Shows"),
                SubcategoryDef("Music"),
                SubcategoryDef("Games"),
                SubcategoryDef("Hobbies"),
                SubcategoryDef("Books"),
                SubcategoryDef("Sports"),
                SubcategoryDef("Concerts/Events")
            )
        ),
        CategoryDef(
            name = "Travel & Vacation",
            type = CategoryType.EXPENSE,
            icon = "flight",
            subcategories = listOf(
                SubcategoryDef("Airfare"),
                SubcategoryDef("Hotels"),
                SubcategoryDef("Car Rental"),
                SubcategoryDef("Vacation Activities")
            )
        ),
        CategoryDef(
            name = "Education",
            type = CategoryType.EXPENSE,
            icon = "school",
            subcategories = listOf(
                SubcategoryDef("Tuition"),
                SubcategoryDef("Books & Supplies"),
                SubcategoryDef("Student Loans")
            )
        ),
        CategoryDef(
            name = "Children",
            type = CategoryType.EXPENSE,
            icon = "child_care",
            subcategories = listOf(
                SubcategoryDef("Childcare"),
                SubcategoryDef("School Expenses"),
                SubcategoryDef("Activities"),
                SubcategoryDef("Allowance")
            )
        ),
        CategoryDef(
            name = "Pets",
            type = CategoryType.EXPENSE,
            icon = "pets",
            subcategories = listOf(
                SubcategoryDef("Pet Food"),
                SubcategoryDef("Vet Bills"),
                SubcategoryDef("Pet Supplies")
            )
        ),
        CategoryDef(
            name = "Financial",
            type = CategoryType.EXPENSE,
            icon = "account_balance",
            subcategories = listOf(
                SubcategoryDef("Bank Fees"),
                SubcategoryDef("Credit Card Fees"),
                SubcategoryDef("ATM Fees"),
                SubcategoryDef("Investment Fees")
            )
        ),
        CategoryDef(
            name = "Taxes",
            type = CategoryType.EXPENSE,
            icon = "receipt_long",
            subcategories = listOf(
                SubcategoryDef("Federal Tax"),
                SubcategoryDef("State Tax"),
                SubcategoryDef("Local Tax"),
                SubcategoryDef("Tax Preparation")
            )
        ),
        CategoryDef(
            name = "Debt Payments",
            type = CategoryType.EXPENSE,
            icon = "credit_card",
            subcategories = listOf(
                SubcategoryDef("Credit Card Payment"),
                SubcategoryDef("Personal Loan"),
                SubcategoryDef("Other Debt")
            )
        ),
        CategoryDef(
            name = "Donations & Charity",
            type = CategoryType.EXPENSE,
            icon = "volunteer_activism"
        ),
        CategoryDef(
            name = "Miscellaneous",
            type = CategoryType.EXPENSE,
            icon = "more_horiz"
        )
    )

    val transferCategories = listOf(
        CategoryDef(
            name = "Transfer to Savings",
            type = CategoryType.TRANSFER,
            icon = "savings"
        ),
        CategoryDef(
            name = "Transfer to Investment",
            type = CategoryType.TRANSFER,
            icon = "trending_up"
        ),
        CategoryDef(
            name = "Account Transfer",
            type = CategoryType.TRANSFER,
            icon = "sync_alt"
        )
    )

    val allCategories: List<CategoryDef>
        get() = incomeCategories + expenseCategories + transferCategories
}
