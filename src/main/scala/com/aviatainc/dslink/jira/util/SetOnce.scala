package com.aviatainc.dslink.jira.util

/**
 * Class which wraps a value which is not set upon initialization,
 * but once set, can never be set again.
 */
class SetOnce[T] {
  private var value: Option[T] = None
  
  /**
   * Sets the value if it has not yet been set.
   * If the value supplied is null, this remains un-set.
   * 
   * @param value the value to which this should be set
   * 
   * @return true if the value was set, otherwise false
   */
  def set(value: T): Boolean = { this.synchronized {
    this.value match {
      case Some(_) => false
      case None => {
        this.value = Option(value)
        isSet
      }
    }
  } }
  
  def get: Option[T] = value
  def isSet: Boolean = value.isDefined
}