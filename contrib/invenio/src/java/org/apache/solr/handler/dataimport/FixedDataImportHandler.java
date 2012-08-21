/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.solr.handler.dataimport;

import static org.apache.solr.handler.dataimport.DataImporter.IMPORT_CMD;

import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.params.UpdateParams;
import org.apache.solr.common.util.ContentStreamBase;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.common.util.ContentStream;
import org.apache.solr.common.util.StrUtils;
import org.apache.solr.util.SystemIdResolver;
import org.apache.solr.core.SolrCore;
import org.apache.solr.handler.RequestHandlerBase;
import org.apache.solr.handler.RequestHandlerUtils;
import org.apache.solr.response.RawResponseWriter;
import org.apache.solr.request.LocalSolrQueryRequest;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.request.SolrRequestHandler;
import org.apache.solr.update.processor.UpdateRequestProcessor;
import org.apache.solr.update.processor.UpdateRequestProcessorChain;
import org.apache.solr.util.plugin.SolrCoreAware;

import java.util.*;
import java.io.StringReader;
import java.lang.reflect.Constructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.InputSource;

/**
 * This is a fixed version of teh DIH, see SOLR-3671
 * 
 * If that is accepted, we can just remove this
 */
public class FixedDataImportHandler extends RequestHandlerBase implements
        SolrCoreAware {

  private static final Logger LOG = LoggerFactory.getLogger(DataImportHandler.class);

  private DataImporter importer;

  private Map<String, Properties> dataSources = new HashMap<String, Properties>();

  private boolean debugEnabled = true;

  private String myName = "dataimport";

  private static final String PARAM_WRITER_IMPL = "writerImpl";
  private static final String DEFAULT_WRITER_NAME = "SolrWriter";

  @Override
  @SuppressWarnings("unchecked")
  public void init(NamedList args) {
    super.init(args);
  }

  @SuppressWarnings("unchecked")
  public void inform(SolrCore core) {
    try {
      //hack to get the name of this handler
      for (Map.Entry<String, SolrRequestHandler> e : core.getRequestHandlers().entrySet()) {
        SolrRequestHandler handler = e.getValue();
        //this will not work if startup=lazy is set
        if( this == handler) {
          String name= e.getKey();
          if(name.startsWith("/")){
            myName = name.substring(1);
          }
          // some users may have '/' in the handler name. replace with '_'
          myName = myName.replaceAll("/","_") ;
        }
      }
      debugEnabled = StrUtils.parseBool((String)initArgs.get(ENABLE_DEBUG), true);
      NamedList defaults = (NamedList) initArgs.get("defaults");
      if (defaults != null) {
        String configLoc = (String) defaults.get("config");
        if (configLoc != null && configLoc.length() != 0) {
          processConfiguration(defaults);
          final InputSource is = new InputSource(core.getResourceLoader().openResource(configLoc));
          is.setSystemId(SystemIdResolver.createSystemIdFromResourceName(configLoc));
          importer = new DataImporter(core, myName);
        }
      }
    } catch (Throwable e) {
      LOG.error( DataImporter.MSG.LOAD_EXP, e);
      throw new SolrException(SolrException.ErrorCode.SERVER_ERROR,
              DataImporter.MSG.INVALID_CONFIG, e);
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public void handleRequestBody(SolrQueryRequest req, SolrQueryResponse rsp)
          throws Exception {
    rsp.setHttpCaching(false);
    
    //TODO: figure out why just the first one is OK...
    ContentStream contentStream = null;
    Iterable<ContentStream> streams = req.getContentStreams();
    if(streams != null){
      for (ContentStream stream : streams) {
          contentStream = stream;
          break;
      }
    }
    SolrParams params = req.getParams();
    NamedList defaultParams = (NamedList) initArgs.get("defaults");
    RequestInfo requestParams = new RequestInfo(getParamsMap(fixWI(params)), contentStream);
    String command = requestParams.getCommand();
   
    
    if (DataImporter.SHOW_CONF_CMD.equals(command)) {
      // Modify incoming request params to add wt=raw
      ModifiableSolrParams rawParams = new ModifiableSolrParams(req.getParams());
      rawParams.set(CommonParams.WT, "raw");
      req.setParams(rawParams);
      String dataConfigFile = defaults.get("config");
      ContentStreamBase content = new ContentStreamBase.StringStream(SolrWriter
              .getResourceAsString(req.getCore().getResourceLoader().openResource(
              dataConfigFile)));
      rsp.add(RawResponseWriter.CONTENT, content);
      return;
    }

    rsp.add("initArgs", initArgs);
    String message = "";

    if (command != null)
      rsp.add("command", command);

    if (requestParams.isDebug() && (importer == null || !importer.isBusy())) {
      // Reload the data-config.xml
      importer = null;
      if (requestParams.getDataConfig() != null) {
        try {
          processConfiguration((NamedList) initArgs.get("defaults"));
          importer = new DataImporter(req.getCore(), myName);
        } catch (RuntimeException e) {
          rsp.add("exception", DebugLogger.getStacktraceString(e));
          importer = null;
          return;
        }
      } else {
        inform(req.getCore());
      }
      message = DataImporter.MSG.CONFIG_RELOADED;
    }

    // If importer is still null
    if (importer == null) {
      rsp.add("status", DataImporter.MSG.NO_INIT);
      return;
    }

    if (command != null && DataImporter.ABORT_CMD.equals(command)) {
      importer.runCmd(requestParams, null);
    } else if (importer.isBusy()) {
      message = DataImporter.MSG.CMD_RUNNING;
    } else if (command != null) {
      if (DataImporter.FULL_IMPORT_CMD.equals(command)
              || DataImporter.DELTA_IMPORT_CMD.equals(command) ||
              IMPORT_CMD.equals(command)) {
    	  
        importer.maybeReloadConfiguration(requestParams, defaultParams);

        UpdateRequestProcessorChain processorChain =
                req.getCore().getUpdateProcessingChain(params.get(UpdateParams.UPDATE_CHAIN));
        UpdateRequestProcessor processor = processorChain.createProcessor(req, rsp);
        DIHWriter sw = getSolrWriter(processor, req);
        
        if (requestParams.isDebug()) {
          if (debugEnabled) {
            // Synchronous request for the debug mode
            importer.runCmd(requestParams, (SolrWriter)sw);
            rsp.add("mode", "debug");
            rsp.add("documents", requestParams.getDebugInfo().debugDocuments);
            if (requestParams.getDebugInfo().debugVerboseOutput != null) {
              rsp.add("verbose-output", requestParams.getDebugInfo().debugVerboseOutput);
            }
          } else {
            message = DataImporter.MSG.DEBUG_NOT_ENABLED;
          }
        } else {
          // Asynchronous request for normal mode
          if(requestParams.getContentStream() == null && !requestParams.isSyncMode()){
            importer.runAsync(requestParams, (SolrWriter)sw);
          } else {
            importer.runCmd(requestParams, (SolrWriter)sw);
          }
        }
      } else if (DataImporter.RELOAD_CONF_CMD.equals(command)) {
          if(importer.maybeReloadConfiguration(requestParams, defaultParams)) {
              message = DataImporter.MSG.CONFIG_RELOADED;
            } else {
              message = DataImporter.MSG.CONFIG_NOT_RELOADED;
            }
      }
    }
    rsp.add("status", importer.isBusy() ? "busy" : "idle");
    rsp.add("importResponse", message);
    rsp.add("statusMessages", importer.getStatusMessages());

    RequestHandlerUtils.addExperimentalFormatWarning(rsp);
  }

  private Map<String, Object> getParamsMap(SolrParams params) {
    Iterator<String> names = params.getParameterNamesIterator();
    Map<String, Object> result = new HashMap<String, Object>();
    while (names.hasNext()) {
      String s = names.next();
      String[] val = params.getParams(s);
      if (val == null || val.length < 1)
        continue;
      if (val.length == 1)
        result.put(s, val[0]);
      else
        result.put(s, Arrays.asList(val));
    }
    return result;
  }

  @SuppressWarnings("unchecked")
  private void processConfiguration(NamedList defaults) {
    if (defaults == null) {
      LOG.info("No configuration specified in solrconfig.xml for DataImportHandler");
      return;
    }

    LOG.info("Processing configuration from solrconfig.xml: " + defaults);

    dataSources = new HashMap<String, Properties>();

    int position = 0;

    while (position < defaults.size()) {
      if (defaults.getName(position) == null)
        break;

      String name = defaults.getName(position);
      if (name.equals("datasource")) {
        NamedList dsConfig = (NamedList) defaults.getVal(position);
        Properties props = new Properties();
        for (int i = 0; i < dsConfig.size(); i++)
          props.put(dsConfig.getName(i), dsConfig.getVal(i));
        LOG.info("Adding properties to datasource: " + props);
        dataSources.put((String) dsConfig.get("name"), props);
      }
      position++;
    }
  }
  
  // DIH is awful, awful.... this can be removed if the SOLR-3671 is accepted
  private SolrParams fixWI(SolrParams params) {
    if(params!=null && params.get(PARAM_WRITER_IMPL) != null) {
      ModifiableSolrParams mParams = new ModifiableSolrParams(params);
      mParams.set(PARAM_WRITER_IMPL, null);
      return mParams;
    }
    else {
      return params;
    }

  }
  @SuppressWarnings("unchecked")
  protected DIHWriter getSolrWriter(final UpdateRequestProcessor processor,
                                   SolrQueryRequest req) {

    SolrParams reqParams = req.getParams();
    String writerClassStr = null;
    if(reqParams!=null && reqParams.get(PARAM_WRITER_IMPL) != null) {
      writerClassStr = (String) reqParams.get(PARAM_WRITER_IMPL);
    }
    
    DIHWriter writer;
    if(writerClassStr != null && !writerClassStr.equals(DEFAULT_WRITER_NAME) && !writerClassStr.equals(DocBuilder.class.getPackage().getName() + "." + DEFAULT_WRITER_NAME)) {
      
      try {
        Class<DIHWriter> writerClass = loadClass(writerClassStr, req.getCore());
        Constructor<DIHWriter> cnstr = writerClass.getConstructor(new Class[]{UpdateRequestProcessor.class, SolrQueryRequest.class});
        return cnstr.newInstance((Object)processor, (Object)req);
      } catch (Exception e) {
        throw new DataImportHandlerException(DataImportHandlerException.SEVERE, "Unable to load Writer implementation:" + writerClassStr, e);
      }
    } else {
      return new SolrWriter(processor, req) {

        @Override
        public boolean upload(SolrInputDocument document) {
          try {
            return super.upload(document);
          } catch (RuntimeException e) {
            LOG.error( "Exception while adding: " + document, e);
            return false;
          }
        }
      };
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public NamedList getStatistics() {
    if (importer == null)
      return super.getStatistics();

    DocBuilder.Statistics cumulative = importer.cumulativeStatistics;
    NamedList result = new NamedList();

    result.add("Status", importer.getStatus().toString());

    if (importer.docBuilder != null) {
      DocBuilder.Statistics running = importer.docBuilder.importStatistics;
      result.add("Documents Processed", running.docCount);
      result.add("Requests made to DataSource", running.queryCount);
      result.add("Rows Fetched", running.rowsCount);
      result.add("Documents Deleted", running.deletedDocCount);
      result.add("Documents Skipped", running.skipDocCount);
    }

    result.add(DataImporter.MSG.TOTAL_DOC_PROCESSED, cumulative.docCount);
    result.add(DataImporter.MSG.TOTAL_QUERIES_EXECUTED, cumulative.queryCount);
    result.add(DataImporter.MSG.TOTAL_ROWS_EXECUTED, cumulative.rowsCount);
    result.add(DataImporter.MSG.TOTAL_DOCS_DELETED, cumulative.deletedDocCount);
    result.add(DataImporter.MSG.TOTAL_DOCS_SKIPPED, cumulative.skipDocCount);

    NamedList requestStatistics = super.getStatistics();
    if (requestStatistics != null) {
      for (int i = 0; i < requestStatistics.size(); i++) {
        result.add(requestStatistics.getName(i), requestStatistics.getVal(i));
      }
    }

    return result;
  }
  
  @SuppressWarnings("unchecked")
  static Class loadClass(String name, SolrCore core) throws ClassNotFoundException {
    try {
      return core != null ?
              core.getResourceLoader().findClass(name, Object.class) :
              Class.forName(name);
    } catch (Exception e) {
      try {
        String n = DataImporter.class.getPackage().getName() + "." + name;
        return core != null ?
                core.getResourceLoader().findClass(n, Object.class) :
                Class.forName(n);
      } catch (Exception e1) {
        throw new ClassNotFoundException("Unable to load " + name + " or " + DataImporter.class.getPackage().getName() + "." + name, e);
      }
    }
  }

  // //////////////////////SolrInfoMBeans methods //////////////////////

  @Override
  public String getDescription() {
    return DataImporter.MSG.JMX_DESC;
  }

  @Override
  public String getSource() {
    return "$URL: http://svn.apache.org/repos/asf/lucene/dev/trunk/solr/contrib/dataimporthandler/src/java/org/apache/solr/handler/dataimport/DataImportHandler.java $";
  }

  public static final String ENABLE_DEBUG = "enableDebug";
}
