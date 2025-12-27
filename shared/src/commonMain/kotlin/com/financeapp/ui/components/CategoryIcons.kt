package com.financeapp.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Icon mappings for transaction categories.
 * Provides consistent iconography across the application.
 *
 * Usage:
 * ```
 * val icon = CategoryIcons.getIcon("Groceries")
 * Icon(imageVector = icon, contentDescription = "Groceries")
 * ```
 *
 * Note: Uses a limited set of Material icons that are guaranteed to exist in Compose Multiplatform.
 * Icons used: ShoppingCart, Home, Settings, Phone, Warning, Star, Face, Build, AccountCircle, Add, Delete, Favorite
 */
object CategoryIcons {

    /**
     * Default icon for unmapped categories
     */
    private val DEFAULT_ICON = Icons.Default.AccountCircle

    /**
     * Category name to icon mapping (50+ categories)
     * Using only the most basic Material icons available
     */
    private val iconMap = mapOf(
        // Food & Dining
        "Groceries" to Icons.Default.ShoppingCart,
        "Restaurants" to Icons.Default.ShoppingCart,
        "Coffee Shops" to Icons.Default.ShoppingCart,
        "Fast Food" to Icons.Default.ShoppingCart,
        "Alcohol & Bars" to Icons.Default.ShoppingCart,
        "Food Delivery" to Icons.Default.ShoppingCart,

        // Shopping
        "Clothing" to Icons.Default.ShoppingCart,
        "Electronics" to Icons.Default.Star,
        "Hobbies" to Icons.Default.Star,
        "Sporting Goods" to Icons.Default.Star,
        "Books" to Icons.Default.Star,
        "Music" to Icons.Default.Star,
        "Movies & DVDs" to Icons.Default.Star,
        "Gifts" to Icons.Default.Star,
        "Online Shopping" to Icons.Default.ShoppingCart,

        // Transportation
        "Gas & Fuel" to Icons.Default.Warning,
        "Parking" to Icons.Default.Warning,
        "Public Transportation" to Icons.Default.Star,
        "Taxi & Rideshare" to Icons.Default.Star,
        "Auto Payment" to Icons.Default.Warning,
        "Auto Insurance" to Icons.Default.Warning,
        "Auto Maintenance" to Icons.Default.Build,

        // Bills & Utilities
        "Rent" to Icons.Default.Home,
        "Mortgage" to Icons.Default.Home,
        "HOA Dues" to Icons.Default.Home,
        "Utilities" to Icons.Default.Settings,
        "Electric" to Icons.Default.Settings,
        "Water" to Icons.Default.Settings,
        "Gas" to Icons.Default.Settings,
        "Internet" to Icons.Default.Settings,
        "Cable & Satellite" to Icons.Default.Settings,
        "Phone" to Icons.Default.Phone,
        "Mobile Phone" to Icons.Default.Phone,

        // Health & Fitness
        "Gym" to Icons.Default.Star,
        "Doctor" to Icons.Default.Warning,
        "Dentist" to Icons.Default.Warning,
        "Pharmacy" to Icons.Default.Warning,
        "Health Insurance" to Icons.Default.Warning,
        "Sports" to Icons.Default.Star,
        "Yoga" to Icons.Default.Star,

        // Entertainment
        "Entertainment" to Icons.Default.Star,
        "Movies & Theater" to Icons.Default.Star,
        "Concerts" to Icons.Default.Star,
        "Streaming Services" to Icons.Default.Star,
        "Gaming" to Icons.Default.Star,

        // Travel
        "Vacation" to Icons.Default.Star,
        "Hotel" to Icons.Default.Home,
        "Airfare" to Icons.Default.Star,
        "Car Rental" to Icons.Default.Star,
        "Travel" to Icons.Default.Star,

        // Personal Care
        "Hair" to Icons.Default.Face,
        "Spa & Massage" to Icons.Default.Face,
        "Personal Care" to Icons.Default.Face,
        "Laundry" to Icons.Default.Home,

        // Pets
        "Pet Food & Supplies" to Icons.Default.Star,
        "Pet Grooming" to Icons.Default.Star,
        "Veterinary" to Icons.Default.Warning,

        // Kids
        "Child Care" to Icons.Default.Face,
        "Toys" to Icons.Default.Star,
        "Baby Supplies" to Icons.Default.ShoppingCart,
        "Allowance" to Icons.Default.AccountCircle,
        "Kids Activities" to Icons.Default.Star,

        // Education
        "Tuition" to Icons.Default.Settings,
        "Student Loan" to Icons.Default.Settings,
        "Books & Supplies" to Icons.Default.ShoppingCart,

        // Financial
        "ATM Fee" to Icons.Default.Warning,
        "Bank Fee" to Icons.Default.Warning,
        "Interest Charged" to Icons.Default.Warning,
        "Finance Charge" to Icons.Default.Warning,
        "Late Fee" to Icons.Default.Warning,
        "Service Fee" to Icons.Default.Warning,
        "Credit Card Payment" to Icons.Default.AccountCircle,
        "Transfer" to Icons.Default.Star,

        // Income
        "Paycheck" to Icons.Default.AccountCircle,
        "Bonus" to Icons.Default.Star,
        "Interest Income" to Icons.Default.Add,
        "Dividend Income" to Icons.Default.Add,
        "Investment Income" to Icons.Default.Add,
        "Refund" to Icons.Default.Add,
        "Reimbursement" to Icons.Default.Add,
        "Rental Income" to Icons.Default.Home,

        // Charity
        "Charity" to Icons.Default.Favorite,
        "Donations" to Icons.Default.Favorite,
        "Religious Donations" to Icons.Default.Favorite,

        // Miscellaneous
        "Cash" to Icons.Default.AccountCircle,
        "Check" to Icons.Default.AccountCircle,
        "Uncategorized" to Icons.Default.Star,
        "Other" to Icons.Default.Star,
        "Misc" to Icons.Default.Star,
        "General" to Icons.Default.Settings,

        // Business
        "Advertising" to Icons.Default.Star,
        "Office Supplies" to Icons.Default.ShoppingCart,
        "Shipping" to Icons.Default.ShoppingCart,
        "Legal" to Icons.Default.Settings,
        "Taxes" to Icons.Default.Warning,

        // Insurance
        "Insurance" to Icons.Default.Settings,
        "Life Insurance" to Icons.Default.Settings,
        "Home Insurance" to Icons.Default.Home,
    )

    /**
     * Get icon for a category name (case-insensitive, partial match)
     */
    fun getIcon(categoryName: String?): ImageVector {
        if (categoryName == null) return DEFAULT_ICON

        // Try exact match first
        iconMap[categoryName]?.let { return it }

        // Try case-insensitive exact match
        iconMap.entries.find { it.key.equals(categoryName, ignoreCase = true) }?.let {
            return it.value
        }

        // Try partial match (e.g., "food" matches "Fast Food")
        iconMap.entries.find {
            it.key.contains(categoryName, ignoreCase = true) ||
            categoryName.contains(it.key, ignoreCase = true)
        }?.let {
            return it.value
        }

        return DEFAULT_ICON
    }

    /**
     * Get all available category names
     */
    fun getAllCategoryNames(): List<String> = iconMap.keys.toList().sorted()

    /**
     * Check if a category has a custom icon
     */
    fun hasCustomIcon(categoryName: String?): Boolean {
        if (categoryName == null) return false
        return iconMap.containsKey(categoryName) ||
               iconMap.keys.any { it.equals(categoryName, ignoreCase = true) }
    }
}
