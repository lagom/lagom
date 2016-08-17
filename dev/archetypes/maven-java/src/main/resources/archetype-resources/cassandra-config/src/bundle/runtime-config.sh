# Demonstrate providing an entirely new configuration directory
CURRENT_PATH=`dirname "$0"`
export $CASSANDRA_CONF=$CURRENT_PATH/cassandra-conf