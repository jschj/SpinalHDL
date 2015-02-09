/*
 * SpinalHDL
 * Copyright (c) Dolu, All rights reserved.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.
 */

package spinal

import scala.collection.mutable

/**
 * Created by PIC18F on 11.01.2015.
 */
object when {
  val stack = new SafeStack[when]


  def doWhen(w: when, isTrue: Boolean)(block: => Unit): when = {
    w.isTrue = isTrue
    stack.push(w)
    block
    stack.pop(w)
    w
  }

  def apply(cond: Bool)(block: => Unit): when = {
    doWhen(new when(cond), true)(block)
  }


  def push(w: when): Unit = {
    stack.push(w)
  }

  def pop(w: when): Unit = {
    stack.pop(w)
  }

  def getWhensCond(that: ContextUser): Bool = {
    val whenScope = if (that == null) null else that.whenScope

    var ret: Bool = null
    for (w <- when.stack.stack) {
      if (w == whenScope) return returnFunc
      val newCond = (if (w.isTrue) w.cond else !w.cond)
      if (ret == null) {
        ret = newCond
      } else {
        ret = ret && newCond
      }
    }


    def returnFunc = if (ret == null) Bool(true) else ret

    returnFunc
  }
}

class when(val cond: Bool) {
  var isTrue: Boolean = true;
  var parentElseWhen: when = null

  def otherwise(block: => Unit): Unit = {
    restackElseWhen
    when.doWhen(this, false)(block)
    destackElseWhen
  }

  def elsewhen(cond: Bool)(block: => Unit): when = {
    var w: when = null
    otherwise({
      w = when(cond) {
        block
      }
      w.parentElseWhen = this
    })
    w
  }

  def restackElseWhen: Unit = {
    if (parentElseWhen == null) return
    parentElseWhen.restackElseWhen
    when.push(parentElseWhen)
  }

  def destackElseWhen: Unit = {
    if (parentElseWhen == null) return
    when.pop(parentElseWhen)
    parentElseWhen.restackElseWhen
  }

  val autoGeneratedMuxs = mutable.Set[Node]()

}

class SwitchStack(val value: Data) {
  // var lastWhen : when = null
}

object switch {
  val stack = new SafeStack[SwitchStack]

  def apply[T <: Data](value: T)(block: => Unit): Unit = {
    val s = new SwitchStack(value)
    stack.push(s)
    block
    stack.pop(s)
  }
}


object is {
  def apply[T <: Data](value: T)(block: => Unit): Unit = is(value :: Nil)(block)

  def apply[T <: Data](value: T, values: T*)(block: => Unit): Unit = is(value :: values.toList)(block)
  //TODO bether mlitple arguments
  def apply[T <: Data](keys: Iterable[T])(block: => Unit): Unit = {
    if (switch.stack.isEmpty) SpinalError("Use 'is' statement outside the 'switch'")
    if (keys.isEmpty) SpinalError("There is no key in 'is' statement")
    val value = switch.stack.head()

    when(keys.map(key => (key === value.value)).reduceLeft(_ || _)) {
      block
    }
  }
}