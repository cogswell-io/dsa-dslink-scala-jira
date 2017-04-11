package com.aviatainc.dslink.jira.services

import scaldi.Injectable
import scaldi.Injector
import io.cogswell.dslink.pubsub.services.AppInjector
import io.cogswell.dslink.pubsub.services.PubSubService
import scala.reflect.runtime.universe

object Services {
  private lazy val injector = new AppInjector{}.injector
  private lazy val serviceProvider: ServiceProvider = new ServiceProvider()(injector)
  
  private var customInjector: Option[Injector] = None
  private var customServiceProvider: Option[ServiceProvider] = None
  
  def serviceInjector: Injector = injector
  
  def useCustomInjector(injector: Injector): Unit = {
    customInjector = Some(injector)
    customServiceProvider = Some(new ServiceProvider()(injector))
  }
  
  def useDefaultInjector(): Unit = {
    customInjector = None
    customServiceProvider = None
  }
  
  def pubSubService = serviceProvider.pubSubService
}

private class ServiceProvider(implicit inj: Injector) extends Injectable {
  val pubSubService = inject [PubSubService]
}