package org.kvxd.kiwi.control.movement

interface MergeableAction : PlayerAction {

    fun merge(other: MergeableAction): MergeableAction?
}