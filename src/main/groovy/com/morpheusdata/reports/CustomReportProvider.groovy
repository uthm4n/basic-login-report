package com.morpheusdata.reports

import com.morpheusdata.core.AbstractReportProvider
import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.core.Plugin
import com.morpheusdata.model.OptionType
import com.morpheusdata.model.ReportResult
import com.morpheusdata.model.ReportType
import com.morpheusdata.model.ReportResultRow
import com.morpheusdata.model.ContentSecurityPolicy
import com.morpheusdata.views.HTMLResponse
import com.morpheusdata.views.ViewModel
import com.morpheusdata.response.ServiceResponse
import groovy.sql.GroovyRowResult
import groovy.sql.Sql
import groovy.util.logging.Slf4j
import io.reactivex.Observable;
import java.util.Date

import java.sql.Connection

@Slf4j
class CustomReportProvider extends AbstractReportProvider {
  Plugin plugin
  MorpheusContext morpheusContext

  CustomReportProvider(Plugin plugin, MorpheusContext context) {
    this.plugin = plugin
    this.morpheusContext = context
  }

  @Override
  MorpheusContext getMorpheus() {
    morpheusContext
  }

  @Override
  Plugin getPlugin() {
    plugin
  }

  // Define the Morpheus code associated with the plugin
  @Override
  String getCode() {
    'user-logins'
  }

  // Define the name of the report displayed on the reports page
  @Override
  String getName() {
    'User Login Summary Report'
  }

   ServiceResponse validateOptions(Map opts) {
     return ServiceResponse.success()
   }

  @Override
  HTMLResponse renderTemplate(ReportResult reportResult, Map<String, List<ReportResultRow>> reportRowsBySection) {
    ViewModel<String> model = new ViewModel<String>()
    model.object = reportRowsBySection
    getRenderer().renderTemplate("hbs/loginReport", model)
  }

  	@Override
	ContentSecurityPolicy getContentSecurityPolicy() {
		def csp = new ContentSecurityPolicy()
		csp
	}

  void process(ReportResult reportResult) {
    // Update the status of the report (generating) - https://developer.morpheusdata.com/api/com/morpheusdata/model/ReportResult.Status.html
    morpheus.report.updateReportResultStatus(reportResult,ReportResult.Status.generating).blockingGet();
    Long displayOrder = 0
    List<GroovyRowResult> results = []
    Connection dbConnection

    try {
      // Create a read-only database connection
      dbConnection = morpheus.report.getReadOnlyDatabaseConnection().blockingGet()
      // Evaluate if a search filter or phrase has been defined
        results = new Sql(dbConnection).rows("SELECT user_id, COUNT(*) AS total_logins_to_date, (SELECT username FROM user WHERE user.id=audit_log.user_id)username FROM audit_log WHERE event_type like '%login%' GROUP BY user_id;")
      // Close the database connection
    } finally {
      morpheus.report.releaseDatabaseConnection(dbConnection)
    }
    log.info("Results: ${results}")
    Observable<GroovyRowResult> observable = Observable.fromIterable(results) as Observable<GroovyRowResult>
    observable.map{ resultRow ->
      log.info("Mapping resultRow ${resultRow}")
      Map<String,Object> data = [userID: resultRow.user_id, username: resultRow.username, logins: resultRow.total_logins_to_date ]
      ReportResultRow resultRowRecord = new ReportResultRow(section: ReportResultRow.SECTION_MAIN, displayOrder: displayOrder++, dataMap: data)
      log.info("resultRowRecord: ${resultRowRecord.dump()}")
      return resultRowRecord
    }.buffer(50).doOnComplete {
      morpheus.report.updateReportResultStatus(reportResult,ReportResult.Status.ready).blockingGet();
    }.doOnError { Throwable t ->
      morpheus.report.updateReportResultStatus(reportResult,ReportResult.Status.failed).blockingGet();
    }.subscribe {resultRows ->
      morpheus.report.appendResultRows(reportResult,resultRows).blockingGet()
    }
  }

  // https://developer.morpheusdata.com/api/com/morpheusdata/core/ReportProvider.html#method.summary
  // The description associated with the custom report
   @Override
   String getDescription() {
     return "View a quick summary of user logins - ID, User ID, and total logins"
   }

   // The category of the custom report
   @Override
   String getCategory() {
     return 'login'
   }

   @Override
   Boolean getOwnerOnly() {
     return false
   }

   @Override
   List<OptionType> getOptionTypes() {
    return
   }

   @Override
   Boolean getMasterOnly() {
     return true
   }

   @Override
   Boolean getSupportsAllZoneTypes() {
     return true
   }
  }
