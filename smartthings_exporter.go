// Copyright © 2018 Joel Baranick <jbaranick@gmail.com>
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
// Based on:
// http://github.com/marcopaganini/smartcollector
// (C) 2016 by Marco Paganini <paganini@paganini.net>

package main

import (
	"encoding/json"
	"fmt"
	"github.com/ejsuncy/smartthingg_exporter/gosmart"
	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/promhttp"
	plog "github.com/prometheus/common/log"
	"github.com/prometheus/common/version"
	"golang.org/x/crypto/ssh/terminal"
	"golang.org/x/net/context"
	"golang.org/x/oauth2"
	"gopkg.in/alecthomas/kingpin.v2"
	"net/http"
	"os"
	"path/filepath"
	"syscall"
)

const (
	namespace = "smartthings"
)

var (
	application = kingpin.New("smartthings_exporter", "Smartthings exporter for Prometheus")

	registerCommand        *kingpin.CmdClause
	registerPort           *uint16
	registerOAuthClient    *string
	registerOAuthTokenFile **os.File

	monitorCommand        *kingpin.CmdClause
	listenAddress         *string
	metricsPath           *string
	monitorOAuthClient    *string
	monitorOAuthTokenFile *string

	metrics = map[string]*prometheus.Desc{}
)

// Exporter collects smartthings stats and exports them using the prometheus metrics package.
type Exporter struct {
	client   *http.Client
	endpoint string
}

// NewExporter returns an initialized Exporter.
func NewExporter(oauthClient string, oauthToken *oauth2.Token) (*Exporter, error) {
	// Create the oauth2.config object with no secret to use with the token we already have
	config := gosmart.NewOAuthConfig(oauthClient, "")

	// Create a client with the token and fetch endpoints URI.
	ctx := context.Background()
	client := config.Client(ctx, oauthToken)
	endpoint, err := gosmart.GetEndPointsURI(client)
	if err != nil {
		plog.Fatalf("Error reading endpoints URI: %v\n", err)
	}

	_, verr := gosmart.GetSensors(client, endpoint)
	if verr != nil {
		plog.Fatalf("Error verifying connection to endpoints URI %v: %v\n", endpoint, err)
	}

	// Init our exporter.
	return &Exporter{
		client:   client,
		endpoint: endpoint,
	}, nil
}

// Describe describes all the metrics ever exported by the Kafka exporter. It
// implements prometheus.Collector.
func (e *Exporter) Describe(ch chan<- *prometheus.Desc) {
	for _, m := range metrics {
		ch <- m
	}
}

// Collect fetches the stats from configured Kafka location and delivers them
// as Prometheus metrics. It implements prometheus.Collector.
func (e *Exporter) Collect(ch chan<- prometheus.Metric) {
	// Iterate over all devices and collect timeseries info.
	sensors, err := gosmart.GetSensors(e.client, e.endpoint)
	if err != nil {
		plog.Errorf("Error reading list of sensors from %v: %v\n", e.endpoint, err)
	}

	for _, sensor := range sensors {
		for _, val := range sensor.Attributes {
			if _, ok := metrics[val.Name]; !ok {
				metric := prometheus.NewDesc(
					prometheus.BuildFQName(namespace, "", val.Name),
					val.Description, []string{"id", "name"}, nil)
				metrics[val.Name] = metric
			}
			ch <- prometheus.MustNewConstMetric(metrics[val.Name], prometheus.GaugeValue, val.Value, sensor.ID, sensor.DisplayName)
		}
	}
}

func init() {
	prometheus.MustRegister(version.NewCollector("smartthings_exporter"))

	registerCommand = application.Command("register", "Register smartthings_exporter with Smartthings and outputs the token.").Action(register)
	registerPort = registerCommand.Flag("register.listen-port", "The port to listen on for the OAuth register.").Default("4567").Uint16()
	registerOAuthClient = registerCommand.Flag("smartthings.oauth-client", "Smartthings OAuth client ID.").Required().String()

	monitorCommand = application.Command("start", "Start the smartthings_exporter.").Default().Action(monitor)
	listenAddress = monitorCommand.Flag("web.listen-address", "Address to listen on for web interface and telemetry.").Default(":9499").String()
	metricsPath = monitorCommand.Flag("web.telemetry-path", "Path under which to expose metrics.").Default("/metrics").String()
	monitorOAuthClient = monitorCommand.Flag("smartthings.oauth-client", "Smartthings OAuth client ID.").Required().String()
	monitorOAuthTokenFile = monitorCommand.Flag("smartthings.oauth-token.file", "File containing the Smartthings OAuth token.").Required().ExistingFile()
}

func main() {
	plog.AddFlags(application)
	application.Version(version.Print("smartthings_exporter"))
	application.HelpFlag.Short('h')
	_, err := application.Parse(os.Args[1:])
	if err != nil {
		application.Fatalf("%s, try --help", err)
	}
}

func register(_ *kingpin.ParseContext) error {
	_, _ = fmt.Fprintln(os.Stderr, "Registering smartthings_exporter with Smartthings")
	_, _ = fmt.Fprintln(os.Stderr, "Enter your Smartthings OAuth secret:")
	bytes, err := terminal.ReadPassword(int(syscall.Stdin))
	if err != nil {
		_, _ = fmt.Fprintln(os.Stderr, "Failed to get Smartthings OAuth secret.")
		return err
	}

	config := gosmart.NewOAuthConfig(*registerOAuthClient, string(bytes))
	gst, err := gosmart.NewAuth(int(*registerPort), config)
	if err != nil {
		_, _ = fmt.Fprintln(os.Stderr, "Failed to create Smartthings OAuth client.")
		return err
	}

	_, _ = fmt.Fprintf(os.Stderr, "Please login by visiting: http://localhost:%d\n", *registerPort)
	token, err := gst.FetchOAuthToken()
	if err != nil {
		_, _ = fmt.Fprintln(os.Stderr, "Failed to fetch Smartthings OAuth token.")
		return err
	}

	blob, err := json.Marshal(token)
	if err != nil {
		_, _ = fmt.Fprintln(os.Stderr, "Failed to serialize Smartthings OAuth token to JSON.",
			(*registerOAuthTokenFile).Name())
		return err
	}

	fmt.Println(string(blob))
	return nil
}

func monitor(_ *kingpin.ParseContext) error {
	plog.Infoln("Starting smartthings_exporter", version.Info())
	plog.Infoln("Build context", version.BuildContext())

	tokenFilePath, err := filepath.Abs(*monitorOAuthTokenFile)
	if err != nil {
		plog.Errorf("Failed to get absolution path to token file %s.\n", *monitorOAuthTokenFile)
		return err
	}

	token, err := gosmart.LoadToken(tokenFilePath)
	if err != nil || !token.Valid() {
		plog.Errorf("Failed to load Smartthings OAuth token from %s.\n", *monitorOAuthTokenFile)
		return err
	}

	exporter, err := NewExporter(*monitorOAuthClient, token)
	if err != nil {
		plog.Fatalln(err)
		return err

	}
	prometheus.MustRegister(exporter)

	http.Handle(*metricsPath, promhttp.Handler())
	http.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
		_, _ = w.Write([]byte(`<html>
			        <head><title>SmartThings Exporter</title></head>
			        <body>
			        <h1>SmartThings Exporter</h1>
			        <p><a href='` + *metricsPath + `'>Metrics</a></p>
			        </body>
			        </html>`))
	})

	plog.Infoln("Listening on", *listenAddress)
	plog.Fatal(http.ListenAndServe(*listenAddress, nil))
	return nil
}
