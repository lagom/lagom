/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.persistence;


import akka.cluster.Cluster;
import akka.pattern.AskTimeoutException;
import java.util.concurrent.CompletionStage;

public class TracingPersistentEntityErrorHandler implements PersistentEntityErrorHandler {

    private final ErrorTracingConfig tracingConfig;
    private final Cluster cluster;
    private final String entityId;

    public TracingPersistentEntityErrorHandler(Cluster cluster, ErrorTracingConfig tracingConfig, String entityId) {
        this.tracingConfig = tracingConfig;
        this.cluster =  cluster;
        this.entityId = entityId;
    }



    public <Reply,Cmd extends Object & PersistentEntity.ReplyType<Reply>> CompletionStage<Reply> handleAskFailure(Throwable failure, Cmd command){
        if(failure instanceof AskTimeoutException){
            if(this.tracingConfig.logClusterStateOnTimeout){
                String detailedMessage = "";
                if(this.tracingConfig.logCommandsPayloadOnTimeout){
                    detailedMessage = " with payload: " + command.toString();
                }
                cluster.system().log().error(failure, "Ask timeout when sending command to  " + entityId  + detailedMessage + " cluster state :" + cluster.state());
            }
            if(this.tracingConfig.logCommandsPayloadOnTimeout){
                String message = failure.getMessage() + " with payload" + command;
                return DefaultPersistentEntityErrorHandler.asFailedReply(new AskTimeoutException(message,failure.getCause()));
            }
            else{
                return DefaultPersistentEntityErrorHandler.asFailedReply(failure);
            }

        }
        else{
            return DefaultPersistentEntityErrorHandler.asFailedReply(failure);
        }
    }


}
