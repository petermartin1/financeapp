package com.financeapp.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Navigation destination for the app
 */
data class NavigationDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val group: NavigationGroup
)

/**
 * Navigation groups for organizing destinations
 */
enum class NavigationGroup {
    MAIN,      // Dashboard, Accounts, Transactions
    TOOLS,     // Budget, Reports, Scheduled, Templates
    DATA,      // Categories, Payees, Tags, Investments
    SETTINGS   // Import, Connections, Backup, Settings
}

/**
 * All navigation destinations in the app
 */
object AppDestinations {
    val Dashboard = NavigationDestination(
        route = "dashboard",
        label = "Dashboard",
        icon = Icons.Default.Home,
        group = NavigationGroup.MAIN
    )

    val Accounts = NavigationDestination(
        route = "accounts",
        label = "Accounts",
        icon = Icons.Default.AccountBalance,
        group = NavigationGroup.MAIN
    )

    val Budget = NavigationDestination(
        route = "budget",
        label = "Budget",
        icon = Icons.Default.PieChart,
        group = NavigationGroup.TOOLS
    )

    val Reports = NavigationDestination(
        route = "reports",
        label = "Reports",
        icon = Icons.Default.Assessment,
        group = NavigationGroup.TOOLS
    )

    val Scheduled = NavigationDestination(
        route = "scheduled",
        label = "Scheduled",
        icon = Icons.Default.Schedule,
        group = NavigationGroup.TOOLS
    )

    val Templates = NavigationDestination(
        route = "templates",
        label = "Templates",
        icon = Icons.Default.Description,
        group = NavigationGroup.TOOLS
    )

    val Categories = NavigationDestination(
        route = "categories",
        label = "Categories",
        icon = Icons.Default.Category,
        group = NavigationGroup.DATA
    )

    val Payees = NavigationDestination(
        route = "payees",
        label = "Payees",
        icon = Icons.Default.People,
        group = NavigationGroup.DATA
    )

    val Tags = NavigationDestination(
        route = "tags",
        label = "Tags",
        icon = Icons.Default.LocalOffer,
        group = NavigationGroup.DATA
    )

    val Investments = NavigationDestination(
        route = "investments",
        label = "Investments",
        icon = Icons.AutoMirrored.Filled.TrendingUp,
        group = NavigationGroup.DATA
    )

    val Import = NavigationDestination(
        route = "import",
        label = "Import",
        icon = Icons.Default.CloudUpload,
        group = NavigationGroup.SETTINGS
    )

    val Connections = NavigationDestination(
        route = "connections",
        label = "Connections",
        icon = Icons.Default.Link,
        group = NavigationGroup.SETTINGS
    )

    val Backup = NavigationDestination(
        route = "backup",
        label = "Backup",
        icon = Icons.Default.Backup,
        group = NavigationGroup.SETTINGS
    )

    val Settings = NavigationDestination(
        route = "settings",
        label = "Settings",
        icon = Icons.Default.Settings,
        group = NavigationGroup.SETTINGS
    )

    /**
     * All destinations grouped by category
     */
    val allDestinations = listOf(
        Dashboard,
        Accounts,
        Budget,
        Reports,
        Scheduled,
        Templates,
        Categories,
        Payees,
        Tags,
        Investments,
        Import,
        Connections,
        Backup,
        Settings
    )

    /**
     * Get destinations by group
     */
    fun getByGroup(group: NavigationGroup) = allDestinations.filter { it.group == group }
}

/**
 * Navigation rail component for desktop app
 * Replaces the dropdown menu with a persistent side navigation
 */
@Composable
fun AppNavigationRail(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    onSearchClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    NavigationRail(
        modifier = modifier.fillMaxHeight(),
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        header = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                IconButton(onClick = onSearchClick) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search transactions",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Main section
        NavigationGroup.MAIN.let { group ->
            AppDestinations.getByGroup(group).forEach { destination ->
                NavigationRailItem(
                    selected = currentRoute == destination.route,
                    onClick = { onNavigate(destination.route) },
                    icon = {
                        Icon(
                            imageVector = destination.icon,
                            contentDescription = destination.label
                        )
                    },
                    label = { Text(destination.label) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
        Spacer(modifier = Modifier.height(8.dp))

        // Tools section
        NavigationGroup.TOOLS.let { group ->
            AppDestinations.getByGroup(group).forEach { destination ->
                NavigationRailItem(
                    selected = currentRoute == destination.route,
                    onClick = { onNavigate(destination.route) },
                    icon = {
                        Icon(
                            imageVector = destination.icon,
                            contentDescription = destination.label
                        )
                    },
                    label = { Text(destination.label) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
        Spacer(modifier = Modifier.height(8.dp))

        // Data section
        NavigationGroup.DATA.let { group ->
            AppDestinations.getByGroup(group).forEach { destination ->
                NavigationRailItem(
                    selected = currentRoute == destination.route,
                    onClick = { onNavigate(destination.route) },
                    icon = {
                        Icon(
                            imageVector = destination.icon,
                            contentDescription = destination.label
                        )
                    },
                    label = { Text(destination.label) }
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
        Spacer(modifier = Modifier.height(8.dp))

        // Settings section at bottom
        NavigationGroup.SETTINGS.let { group ->
            AppDestinations.getByGroup(group).forEach { destination ->
                NavigationRailItem(
                    selected = currentRoute == destination.route,
                    onClick = { onNavigate(destination.route) },
                    icon = {
                        Icon(
                            imageVector = destination.icon,
                            contentDescription = destination.label
                        )
                    },
                    label = { Text(destination.label) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * Compact navigation rail (icon only, no labels)
 */
@Composable
fun CompactAppNavigationRail(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationRail(
        modifier = modifier.fillMaxHeight(),
        containerColor = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        AppDestinations.allDestinations.forEach { destination ->
            NavigationRailItem(
                selected = currentRoute == destination.route,
                onClick = { onNavigate(destination.route) },
                icon = {
                    Icon(
                        imageVector = destination.icon,
                        contentDescription = destination.label
                    )
                }
            )
        }
    }
}
