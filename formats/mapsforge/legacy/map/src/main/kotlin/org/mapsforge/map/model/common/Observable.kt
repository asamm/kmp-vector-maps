/*
 * Copyright 2010, 2011, 2012, 2013 mapsforge.org
 * Copyright 2026 Asamm Software
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.mapsforge.map.model.common

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Keeps observers and notifies them; null or duplicate registrations throw
 * [IllegalArgumentException].
 */
open class Observable : ObservableInterface {

    private val observers: MutableList<Observer> = CopyOnWriteArrayList()

    final override fun addObserver(observer: Observer?) {
        if (observer == null) {
            throw IllegalArgumentException(OBSERVER_MUST_NOT_BE_NULL)
        } else if (observers.contains(observer)) {
            throw IllegalArgumentException("observer is already registered: $observer")
        }
        observers.add(observer)
    }

    final override fun removeObserver(observer: Observer?) {
        if (observer == null) {
            throw IllegalArgumentException(OBSERVER_MUST_NOT_BE_NULL)
        } else if (!observers.contains(observer)) {
            throw IllegalArgumentException("observer is not registered: $observer")
        }
        observers.remove(observer)
    }

    fun notifyObservers() {
        for (observer in observers) {
            observer.onChange()
        }
    }

    private companion object {

        private const val OBSERVER_MUST_NOT_BE_NULL = "observer must not be null"
    }
}
