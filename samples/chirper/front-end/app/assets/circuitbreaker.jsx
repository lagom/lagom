var Route = ReactRouter.Route;
var IndexRoute = ReactRouter.IndexRoute;
var Link = ReactRouter.Link;

function createCircuitBreakerStream(serviceHostPort, onopen) {
    return {
        connect: function(onEvent) {
            var stream = new WebSocket("ws://" + serviceHostPort + "/_status/circuit-breaker/stream");
            if (onopen) {
                stream.onopen = function(event) {
                    onopen(stream, event);
                }.bind(this);
            }
            stream.onmessage = function(event) {
                var event = JSON.parse(event.data);
                onEvent(event);
            }.bind(this);
            return {
                close: function() {
                    stream.close();
                }
            }
        }
    };
}

var CircuitBreaker = React.createClass({
    render: function() {
        return (
            <div className="circuitBreaker">
                {this.props.children}
                <hr />
            </div>
        );
    }
});

var CircuitBreakerStream = React.createClass({
    getInitialState: function() {
        return {circuitBreakers: []};
    },
    componentDidMount: function() {
        this.stream = this.props.stream.connect(function(circuitBreaker) {
            var newCircuitBreakers = circuitBreaker;
            this.setState({circuitBreakers: newCircuitBreakers});
        }.bind(this));
    },
    componentWillUnmount: function() {
        this.stream.close();
    },
    render: function() {
        var circuitBreakerNodes = this.state.circuitBreakers.map(function(cb) {
                var stateElem = <span>{cb.state}</span>
		        return (
		          <CircuitBreaker key={cb.id}>
		            <p><b>{cb.id}</b></p>
		            <table className="invisible-table"><tbody>
		            <tr><td>
    		            <table><tbody>
                          <tr><td>State</td><td><CircuitBrakerState state={cb.state}/></td></tr>
                          <tr><td>Throughput</td><td>{cb.throughputOneMinute.toFixed(0)} msg/s</td></tr>
                          <tr><td>Failed throughput</td><td>{cb.failedThroughputOneMinute} msg/s</td></tr>
                          <tr><td>Latency mean</td><td>{formatLatency(cb.latencyMicros.mean)}</td></tr>
                          <tr><td>Total success count</td><td>{cb.totalSuccessCount}</td></tr>
                          <tr><td>Total failure count</td><td>{cb.totalFailureCount}</td></tr>
                          <tr><td>Timestamp</td><td>{cb.timestamp.toFixed(3)}</td></tr>
    		            </tbody></table>
    		        </td><td>    
    		            <table><tbody>
                          <tr><td>Latency distribution</td><td>&nbsp;</td></tr>
                          <tr><td>min</td><td>{formatLatency(cb.latencyMicros.min)}</td></tr>
                          <tr><td>median</td><td>{formatLatency(cb.latencyMicros.median)}</td></tr>
                          <tr><td>98th percentile</td><td>{formatLatency(cb.latencyMicros.percentile98th)}</td></tr>
                          <tr><td>99th percentile</td><td>{formatLatency(cb.latencyMicros.percentile99th)}</td></tr>
                          <tr><td>99.9th percentile</td><td>{formatLatency(cb.latencyMicros.percentile999th)}</td></tr>
                          <tr><td>max</td><td>{formatLatency(cb.latencyMicros.max)}</td></tr>
                        </tbody></table>
                    </td></tr>
                    </tbody></table>
		          </CircuitBreaker>
		        );
        }.bind(this));
        
        return (
            <div className="circuitBreakerStream">
                <hr />
                {circuitBreakerNodes}
            </div>

        );
    }
});

function formatLatency(value) {
    if (isNaN(value)) {
        return "";
    } else if (value < 1000) {
        return value.toFixed(0) + " µs"
    } else if (value < 10000) {
        return (value / 1000).toFixed(1) + " ms"
    } else {
        return (value / 1000).toFixed(0) + " ms"
    }
}


var CircuitBrakerState = React.createClass({
    render: function() {
        if (this.props.state == "closed") {
            return (<span>{this.props.state}</span>);
        } else {
            return (<span className="error">{this.props.state}</span>);
        }
        
    }
});

var CircuitBreakers = React.createClass({
    render: function() {
        if (!localStorage.serviceHostPort) {
            localStorage.serviceHostPort = "localhost:27462"
        }
        var circuitBreakerStream = <CircuitBreakerStream stream={createCircuitBreakerStream(localStorage.serviceHostPort)}/>;
        return (
            <ContentLayout subtitle={"Circuit Breakers for " + localStorage.serviceHostPort}>
                <Section>
                    <div className="small-12 columns">
                        <ServiceHostPortForm />
                        {circuitBreakerStream}
                    </div>
                </Section>
            </ContentLayout>
        );
    }
});

var ServiceHostPortForm = React.createClass({
    getInitialState: function() {
        return {serviceHostPort: localStorage.serviceHostPort};
    },
    handleMessageChange: function(e) {
        this.setState({serviceHostPort: e.target.value});
    },
    handleSubmit: function(e) {
        var serviceHostPort = this.state.serviceHostPort.trim();
        if (!serviceHostPort) {
            return;
        }
        localStorage.serviceHostPort = serviceHostPort
    },
    render: function() {
        return (
            <form className="serviceHostPortForm" onSubmit={this.handleSubmit}>
                <input type="text"
                   placeholder="host:port (e.g. localhost:27462 or localhost:21360)"
                   value={this.state.serviceHostPort}
                   onChange={this.handleMessageChange}
                />
                <input type="submit" value="Go" />
            </form>
        );
    }
});


var Error = React.createClass({
    render: function() {
        return (
            <div>
                <span className="error">{this.props.message}</span>
            </div>
        );
    }
});

var Section = React.createClass({
    render: function() {
        return (
            <section className="fw-wrapper feature">
                <div className="row">
                    {this.props.children}
                </div>
            </section>
        );
    }
});

var ContentLayout = React.createClass({
   render: function() {
       return (
           <div id="page-content">
               <section id="top">
                   <div className="row">
                       <header className="large-12 columns">
                            <h1>{this.props.subtitle}</h1>
                       </header>
                   </div>
               </section>
               {this.props.children}
           </div>
       );
   }
});

ReactDOM.render(
    <ReactRouter.Router history={History.createHistory()}>
        <Route path="/cb" component={CircuitBreakers} />
    </ReactRouter.Router>,
    document.getElementById("content")
);



