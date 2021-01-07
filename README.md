# smarthings_exporter [![Build Status](https://travis-ci.org/kadaan/smartthings_exporter.svg?branch=master)](https://travis-ci.org/kadaan/smartthings_exporter) [![Coverage Status](https://img.shields.io/coveralls/github/kadaan/smartthings_exporter/master.svg)](https://coveralls.io/github/kadaan/smartthings_exporter) [![Go Report Card](https://goreportcard.com/badge/github.com/kadaan/smartthings_exporter)](https://goreportcard.com/report/github.com/kadaan/smartthings_exporter)

Smartthings_exporter is a command line tool to export information about your SmartThings
sensors in a format that can be scraped by [Prometheus](http://prometheus.io). The tool talks 
to the SmartThings API and collects sensor data which is exposed as metrics over http.

## Installation

The installation instructions assume a properly installed and configured Go
development environment. The very first step is to download and build
Smartthings_exporter (this step will also download and compile the GoSmart library):


```
$ go get -u github.com/ejsuncy/smartthings_exporter
```

### SmartThings Setup

Before you can use Smartthings_exporter, you need to register it with SmartThings.  

The first step is to setup the API that Smartthings_exporter uses to communicate with SmartThings.  To do this you need to:

1. Navigate to the [SmartThings API website](https://graph.api.smartthings.com/). Register a new account (or login if you already have an account).
2. Once logged in, click on My SmartApps. This will show a list of the current SmartApps installed (it could be blank for new accounts).
3. Click the `Settings` button at the top right.
4. Click the `Add new repository` link at the bottom of the settings dialog box.
5. In the new row fill in:
    1. Owner: `kadaan`
    2. Name: `smartthings_exporter`
    3. Branch: `master`
6. Press `Save`
7. Click `Update from Repo` at the top right
8. Choose `smartthings_exporter (master)`
9. Under the `New` list on the right check `smartapps/kadaan/smartthings-exporter-api.src/smartthings-exporter-api.groovy`
10. Check `Publish` at the bottom
11. Click `Execute Update`
12. Click the `Edit Properties` button for the `kadaan : Smartthings Exporter API` entry
13. Click `OAuth`
14. Click `Enable OAuth in SmartApp`
15. In `Redirect URI`, enter `http://localhost:4567/OAuthCallback`. _Case is important here_
16. Click `Update`
17. Click `OAuth`
18. Take note of the `Client ID` and `Client Secret`. These will be used to authenticate and retrieve a token. Once the token is saved locally by the library, authentication can proceed without user intervention.

### Smartthings_exporter configuration

We now need to register Smartthings_exporter to with your SmartThings app.

Run:

```
$ smartthings_exporter register --smartthings.oauth-client=[client_id] > .st_token
```

Follow the prompts to authorize the app.

## Running

Now we can start Smartthings_exporter by running:

```
$ smartthings_exporter --smartthings.oauth-client=[client_id] --smartthings.oauth-token.file=.st_token
```
