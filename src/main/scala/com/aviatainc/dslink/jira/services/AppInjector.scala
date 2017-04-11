package com.aviatainc.dslink.jira.services

import scaldi.Injector

trait AppInjector {
  implicit lazy val injector: Injector = ServicesModule
}