package com.karthikmacherla.metrotracker

import com.karthikmacherla.metrotracker.core.MetroNetwork
import com.karthikmacherla.metrotracker.core.NetworkLoader
import com.karthikmacherla.metrotracker.core.RoutePlanner

/**
 * One copy of the station data for the whole app, shared by the plan screen and the
 * tracking service. Loaded on first use; call from a background thread the first time.
 */
object MetroData {
    val network: MetroNetwork by lazy { NetworkLoader.loadHyderabad() }
    val planner: RoutePlanner by lazy { RoutePlanner(network) }
}
