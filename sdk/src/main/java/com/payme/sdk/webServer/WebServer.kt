package com.payme.sdk.webServer

import java.io.File


class WebServer(localAddr: String?, port: Int, wwwroot: File?) :
    MySimpleWebServer(localAddr, port, wwwroot, true, "*") {
    //    SimpleWebServer(localAddr, port, wwwroot, true, "*") {
    override fun useGzipWhenAccepted(r: Response): Boolean {
        return super.useGzipWhenAccepted(r) && r.status !== Response.Status.NOT_MODIFIED
    }

    init {
        mimeTypes()["xhtml"] = "application/xhtml+xml"
        mimeTypes()["opf"] = "application/oebps-package+xml"
        mimeTypes()["ncx"] = "application/xml"
        mimeTypes()["epub"] = "application/epub+zip"
        mimeTypes()["otf"] = "application/x-font-otf"
        mimeTypes()["ttf"] = "application/x-font-ttf"
        mimeTypes()["js"] = "application/javascript"
        mimeTypes()["svg"] = "image/svg+xml"
        mimeTypes()["png"] = "image/png"
        mimeTypes()["jpg"] = "image/jpg"
        mimeTypes()["css"] = "text/css"
        mimeTypes()["html"] = "text/html"
        mimeTypes()["htm"] = "text/html"
        mimeTypes()["ico"] = "image/x-icon"
        mimeTypes()["map"] = "application/json"
        mimeTypes()["woff2"] = "font/woff2"
        mimeTypes()["woff"] = "font/woff"
        mimeTypes()["eot"] = "application/vnd.ms-fontobject"
        mimeTypes()["txt"] = "text/plain"
        mimeTypes()["json"] = "application/json"
    }
}