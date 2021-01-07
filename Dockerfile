FROM golang:1.15-alpine
RUN apk update && apk upgrade && apk add git
RUN go get github.com/ejsuncy/smartthings_exporter

