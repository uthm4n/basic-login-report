package com.morpheusdata.reports

import com.morpheusdata.core.Plugin

class ReportsPlugin extends Plugin {

  String getCode() {
    'user-logins'
  }

  @Override
  void initialize() {
    CustomReportProvider customReportProvider = new CustomReportProvider(this, morpheus)
    this.pluginProviders.put(customReportProvider.code, customReportProvider)
    this.setName("User Login Report")
    this.setDescription("A custom report plugin for user logins - prints ID, User ID, and the total number of logins ")
  }

  @Override
  void onDestroy() {
  }
}
