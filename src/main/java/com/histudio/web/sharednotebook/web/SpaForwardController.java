package com.histudio.web.sharednotebook.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaForwardController {

	@GetMapping({
		"/notes",
		"/notes/{noteId}",
		"/notes/{noteId}/history",
		"/conflicts/{noteId}",
		"/connection",
		"/admin",
		"/admin/{section}"
	})
	String forwardApplicationRoute() {
		return "forward:/index.html";
	}
}
