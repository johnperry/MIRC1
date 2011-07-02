var inMenu = false;
var timerRunning = false;
var timeoutId = null;
var timeoutPeriod = 1000;
var popupZIndex = 40;
var menuZIndexMax = 30;
var menuZIndexMin = 20;
var current_page;
var current_tab;

function startTimer(handler, delay) {
	stopTimer();
	timeoutId = window.setTimeout(handler, delay);
	timerRunning = true;
}

function stopTimer() {
	if (timerRunning) {
		window.clearTimeout(timeoutId);
		timerRunning = false;
	}
}

function timeoutHandler() {
	stopTimer();
	if (!inMenu) hideMenus();
}

function showMenu(titleId, menuId) {
	hideMenus();
	hidePopups();
	var title = document.getElementById(titleId);
	var pos = findObject(title);
	var menu = document.getElementById(menuId);
	menu.style.left = pos.x - 15;
	menu.style.top = pos.y + pos.h + (document.all ? 1 : 2);
	menu.style.visibility = "visible";
	menu.style.display = "block";
	menu.style.zIndex = menuZIndexMax;
	inMenu = true;
}

function showSubmenu(titleId, submenuId, level) {
	hideRange(menuZIndexMin, menuZIndexMax - level);
	var title = document.getElementById(titleId);
	var top = title.offsetTop;
	var menu = getParentMenu(title);
	if (menu == null) return;
	var pos = findObject(menu);
	var submenu = document.getElementById(submenuId);
	submenu.style.left = pos.x + pos.w - 8;
	submenu.style.top = pos.y + top - 4;
	submenu.style.visibility = "visible";
	submenu.style.display = "block";
	submenu.style.zIndex = menuZIndexMax - level;
	inMenu = true;
}

function checkHideMenus(theEvent) {
	var source = getSource(theEvent);
	var sourceParent = getParentMenu(source);
	var dest = getDestination(theEvent);
	var destParent = getParentMenu(dest);
	if ((destParent == null)
		|| ((sourceParent.className == "menubar")
			&& (destParent.className != "menubar")
			&& (destParent.className != "menu"))) {
		stopTimer();
		timeoutId = window.setTimeout("timeoutHandler();", timeoutPeriod);
		timerRunning = true;
		inMenu = false;
	}
	else inMenu = true;
}

function getParentMenu(object) {
	var parent = object;
	while ((parent != null)
		&& ((parent.className == null) ||
		 	(parent.className.indexOf('menu') != 0))) parent = parent.parentNode;
	return parent;
}

function hideMenus() {
	inMenu = false;
	stopTimer();
	hideRange(menuZIndexMin, menuZIndexMax);
	setCookies();
}

function hideRange(lowerZIndex, upperZIndex) {
	var divs = document.getElementsByTagName("DIV");
	for (var i=0; i<divs.length; i++) {
		var z = divs[i].style.zIndex;
		if ((z >= lowerZIndex) && (z <= upperZIndex)) {
			divs[i].style.visibility = "hidden";
			divs[i].style.display = "none";
		}
	}
}

function menuItemSelected(theEvent) {
//	alert("menuItemSelected called");
}

function showPopup(id, w, h) {
	hideMenus();
	hidePopups();
	var bodyPos = findObject(document.body);
	var menubarPos = findObject(document.getElementById("menubar"));
	var x = (bodyPos.w - w) / 2;
	var menubarBottom = menubarPos.y + menubarPos.h;
	var extraSpace = bodyPos.h - menubarBottom - h
	var y = menubarBottom + extraSpace / 3;
	if (y < 0) y = 0;
	var popup = document.getElementById(id);
	popup.style.width = w;
	popup.style.height = h;
	popup.style.left = x;
	popup.style.top = y;
	popup.style.visibility = "visible";
	popup.style.display = "block";
	popup.style.overflow = "auto";
	popup.style.zIndex = popupZIndex;
}

function showNews() {
	var newsDiv = document.getElementById("news");
	if (newsDiv != null) {
		var goButton = document.getElementById("go");
		var goPos = findObject(goButton);
		newsDiv.style.top = goPos.y;
		newsDiv.style.left = 0;
		newsDiv.style.zIndex = 1;
		newsDiv.style.visibility = "visible";
		newsDiv.style.display = "block";
	}
}

function showServersPopup() {
	var id = 'serversPopup';
	var sp = document.getElementById(id);
	var ssWH = setServerSelectWH();
	showPopup(id, ssWH.w + 30, ssWH.h + 80);
}

function setServerSelectWH() {
	var ss = document.getElementById("serverselect");
	var options = ss.getElementsByTagName("OPTION");
	var h = 15 * options.length + 40;
	if (h > 500) h = 500;
	var w = 440;
	ss.style.height = h + "px";
	ss.style.width = w + "px";
	var wh = new Object();
	wh.w = w;
	wh.h = h;
	return wh;
}

function showPopupFrame(id, w, h, frId, frURL) {
	showPopup(id, w, h);
	var frame = document.getElementById(frId);
	frame.style.width = w - 30;
	frame.style.height = h - 55;
	window.open(frURL,frId);
}

function hidePopups() {
	hideRange(popupZIndex, popupZIndex);
	setCookies();
}

function startDrag(popupDiv, event) {
	deltaX = event.clientX - parseInt(popupDiv.style.left);
	deltaY = event.clientY - parseInt(popupDiv.style.top);
	if (document.addEventListener) {
		document.addEventListener("mousemove", dragPopup, true);
		document.addEventListener("mouseup", dropPopup, true);
	}
	else {
		popupDiv.attachEvent("onmousemove", dragPopup);
		popupDiv.attachEvent("onmouseup", dropPopup);
		popupDiv.setCapture();
	}
	if (event.stopPropagation) event.stopPropagation();
	else event.cancelBubble = true;
	if (event.preventDefault) event.preventDefault();
	else event.returnValue = false;

	function dragPopup(evt) {
		if (!evt) evt = window.event;
		popupDiv.style.left = (evt.clientX - deltaX) + "px";
		popupDiv.style.top = (evt.clientY - deltaY) + "px";
		if (evt.stopPropagation) event.stopPropagation();
		else evt.cancelBubble = true;
	}

	function dropPopup(evt) {
		if (!evt) evt = window.event;
		if (document.addEventListener) {
			document.removeEventListener("mouseup", dropPopup, true);
			document.removeEventListener("mousemove", dragPopup, true);
		}
		else {
			popupDiv.detachEvent("onmousemove", dragPopup);
			popupDiv.detachEvent("onmouseup", dropPopup);
			popupDiv.releaseCapture();
		}
		if (evt.stopPropagation) event.stopPropagation();
		else evt.cancelBubble = true;
	}
}

function findObject(obj) {
	var objPos = new Object();
	objPos.obj = obj;
	objPos.h = obj.offsetHeight;
	objPos.w = obj.offsetWidth;
	var curleft = 0;
	var curtop = 0;
	if (obj.offsetParent) {
		curleft = obj.offsetLeft
		curtop = obj.offsetTop
		while (obj = obj.offsetParent) {
			curleft += obj.offsetLeft
			curtop += obj.offsetTop
		}
	}
	objPos.x = curleft;
	objPos.y = curtop;
	return objPos;
}

function getSource(theEvent) {
	return (document.all) ?
		theEvent.srcElement : theEvent.target;
}

function getDestination(theEvent) {
	return (document.all) ?
		theEvent.toElement : theEvent.relatedTarget;
}

function getEvent(theEvent) {
	return (theEvent) ? theEvent : ((window.event) ? window.event : null);
}

function selectAllServers() {
	var ss = document.getElementById("serverselect");
	var options = ss.getElementsByTagName("OPTION");
	for (var i=0; i<options.length; i++) {
		options[i].selected = true;
	}
}

function onloadOccurred() {
//	listCookies();
	setMenuEnables();
	setState();
	showDiv(startpage);
	showCurrentService();
	current_page = document.getElementById('div1');
	current_tab = document.getElementById('page1tab');
	selectTab(current_tab);
	if (startpage == "maincontent") {
		var freeText = document.getElementById("freetext");
		freeText.focus();
	}
	else if (startpage == "altcontent") {
		var freeText = document.getElementById("altfreetext");
		freeText.focus();
	}
	if (!document.all) {
		var titleDiv = document.getElementById("pagetitle");
		titleDiv.style.color = "blue";
	}
}
window.onload = onloadOccurred;

function showDiv(divid) {
	if (divid == "maincontent") {
		hide("altcontent");
		show("maincontent");
	}
	else {
		hide("maincontent");
		show("altcontent");
	}
	var querypage = document.getElementById("querypage");
	querypage.value = divid;
	showNews();
}

function hide(divid) {
	var div = document.getElementById(divid);
	div.style.display = "none";
	div.style.visibility = "hidden";
}

function show(divid) {
	var div = document.getElementById(divid);
	div.style.display = "block";
	div.style.visibility = "visible";
}

function setBreedList() {
	var ptSpecies = document.getElementById("pt-species");
	var ptBreed = document.getElementById("pt-breed");
	ptBreed.options.length = 0;
	var choice = ptSpecies.selectedIndex;
	var breedlist = breeds[choice];
	ptBreed.options[0] = new Option("","");
	for (var i=0; i<breedlist.length; i++) {
		 ptBreed.options[i+1] = new Option(breedlist[i], breedlist[i]);
	}
}

function bclick(next_page_Id, theEvent) {
	var clicked_tab = getSource(theEvent);
	var next_page = document.getElementById(next_page_Id);
	if (current_page != next_page) {
		current_page.style.visibility="hidden";
		current_page.style.display="none";
		current_page = next_page;
		current_page.style.visibility="visible";
		current_page.style.display="block";
		deselectTab(current_tab);
		current_tab = clicked_tab;
		selectTab(current_tab);
		showNews();
	}
}

function selectTab(tab) {
	tab.style.backgroundColor = "#6495ED";
	tab.style.color = 'white';
}

function deselectTab(tab) {
	tab.style.backgroundColor = 'white';
	tab.style.color = "#6495ED";
}

function login(path) {
	window.open("/mirc/login?path=/mirc/query","_self");
}

function localServiceSelected(service) {
	hideMenus();
	currentservice = service - 1;
	showCurrentService();
	setMenuEnables();
}

function showCurrentService() {
	if (currentservice < storage.length) {
		var currSrv = document.getElementById("currentservice");
		var child;
		while ((child = currSrv.firstChild) != null) currSrv.removeChild(child);
		currSrv.appendChild(document.createTextNode(storage[currentservice].name));
	}
}

function gotoLocalService(path) {
	var adrs = storage[currentservice].context;
	window.open(adrs + path, "_self");
}

function gotoLocalAdmin(n) {
	var adrs = storage[n-1].context;
	window.open(adrs + "/admin", "_self");
}

function clearQueryFields() {
	var form = document.getElementById("queryform");
	var inputs = form.getElementsByTagName("INPUT");
	for (var i=0; i<inputs.length; i++) {
		if (inputs[i].type == "text") inputs[i].value = "";
		else if ((inputs[i].type == "checkbox") &&
				 (inputs[i].name != "unknown") &&
				 (inputs[i].name != "casenavigator") &&
				 (inputs[i].name != "randomize") &&
				 (inputs[i].name != "icons")) inputs[i].checked = false;
	}
	var selects = form.getElementsByTagName("SELECT");
	for (var i=0; i<selects.length; i++) {
		if ((selects[i].name != "serverselect") &&
			(selects[i].name != "maxresults") &&
			(selects[i].name != "display") &&
			(selects[i].name != "bgcolor")) selects[i].selectedIndex = 0;
	}
}

function searchExternalEngine(divId, qName) {
	var text = document.getElementById("freetext").value
			+ " "
			+ document.getElementById("altfreetext").value;
	var div = document.getElementById(divId);
	var form = div.getElementsByTagName("FORM")[0];
	var inputs = form.getElementsByTagName("INPUT");
	for (var i=0; i<inputs.length; i++) {
		if (inputs[i].name == qName) {
			inputs[i].value = text;
			form.submit();
			return;
		}
	}
	alert("Query element "+qName+" not found in "+divId);
}

function Tomcat(theAddress, theAdminRole) {
	this.address = theAddress;
	this.admin = theAdminRole;
}

function QueryService(theAdminRole, theUserRole) {
	this.admin = theAdminRole;
	this.user = theUserRole;
}

function FileService(theAdminRole, theUserRole) {
	this.admin = theAdminRole;
	this.user = theUserRole;
}

function StorageService(
		theContext, theAdminRole, theAuthorRole,
		thePublisherRole, theUpdateRole, theUserRole,
		theName) {
	this.name = theName;
	this.context = theContext;
	this.admin = theAdminRole;
	this.author = theAuthorRole;
	this.publisher = thePublisherRole;
	this.update = theUpdateRole;
	this.user = theUserRole;
}

function setCookies() {
	setTextCookie("querypage");
	setCookie("currentservice",currentservice);
	setSelectCookie("serverselect");
	setSelectCookie("maxresults");
	setSelectCookie("display");
	setSelectCookie("bgcolor");
	setCheckboxCookie("unknown");
	setCheckboxCookie("showimages");
	setCheckboxCookie("casenavigator");
	setCheckboxCookie("randomize");
	setCheckboxCookie("icons");
}

function setCookie(id,value) {
	var path = ";path=/";
	var nextyr = new Date();
	nextyr.setFullYear(nextyr.getFullYear() + 1);
	var expires = ";expires="+nextyr.toGMTString();
	document.cookie = id + "=" + escape(value) + path + expires;
}

function setTextCookie(id) {
	var el = document.getElementById(id);
	var text = el.value;
	setCookie(id,text);
}

function setSelectCookie(id) {
	var el = document.getElementById(id);
	var text = "";
	var opts = el.getElementsByTagName("OPTION");
	for (var i=0; i<opts.length; i++) {
		if (opts[i].selected) {
			if (text != "") text += ":";
			text += i;
		}
	}
	setCookie(id,text);
}

function setCheckboxCookie(id) {
	var el = document.getElementById(id);
	setCookie(id,el.checked);
}

function listCookies() {
	var cooks = getCookieObject();
	var text = "";
	for (x in cooks) {
		text += x + "=" + cooks[x] + "\n";
	}
	alert("Cookies:\n"+text);
}

function getCookieObject() {
	var cookies = new Object();
	var allcookies = document.cookie;
	var cooks = allcookies.split(";");
	for (var i=0; i<cooks.length; i++) {
		cooks[i] = cooks[i].replace(/\s/g,"");
		var cook = cooks[i].split("=");
		cookies[cook[0]] = unescape(cook[1]);
	}
	clearOldCookies(cookies);
	return cookies;
}

function clearOldCookies(cookies) {
	clearCookie("advancedServicesField", cookies);
	clearCookie("currentServerField", cookies);
	clearCookie("advancedQueryField", cookies);
}

function clearCookie(name, cookies) {
	if (cookies[name] != null) {
		var date = new Date();
		date.setFullYear(1980);
		var expires = ";expires="+date.toGMTString();
		document.cookie = name + "=" + "x" + expires;
	}
}

function setState() {
	var cookies = getCookieObject();
	var page = cookies["querypage"];
	if (page != null) startpage = page;
	var cserv = cookies["currentservice"];
	if (cserv != null) currentservice = parseInt(cserv);
	setSelectFromCookie("serverselect", cookies);
	setSelectFromCookie("maxresults", cookies);
	setSelectFromCookie("display", cookies);
	setSelectFromCookie("bgcolor", cookies);
	setCheckboxFromCookie("unknown", cookies);
	setCheckboxFromCookie("showimages", cookies);
	setCheckboxFromCookie("casenavigator", cookies);
	setCheckboxFromCookie("randomize", cookies);
	setCheckboxFromCookie("icons", cookies);
}

function setTextFromCookie(id, cookies) {
	var ctext = cookies[id];
	if (ctext == null) return;
	document.getElementById(id).value = ctext;
}

function setSelectFromCookie(id, cookies) {
	var ctext = cookies[id];
	if (ctext == null) return;
	var ints = ctext.split(":");
	for (var i=0; i<ints.length; i++) {
		ints[i] = parseInt(ints[i]);
	}
	var el = document.getElementById(id);
	var opts = el.getElementsByTagName("OPTION");
	for (var i=0; i<opts.length; i++) {
		if (contains(ints,i)) opts[i].selected = true;
		else opts[i].selected = false;
	}
}

function setCheckboxFromCookie(id, cookies) {
	var ctext = cookies[id];
	if (ctext == null) return;
	document.getElementById(id).checked = (ctext == "true");
}

function contains(ints, i) {
	for (var k=0; k<ints.length; k++) {
		if (ints[k] == i) return true;
	}
	return false;
}

function setMenuEnables() {
	setEnable("MyStuffMenu", (username != "") || (acctenb == "yes"));
	setEnable("ToolsMenu", (username != ""));
	setEnable("AuthorServices", hasAdminRole() || hasAuthorRole());
	setEnable("AdminMenu", hasAdminRole());
	setEnable("InputQueueViewerItem", hasAdminRole() || hasPublisherRole());
}

function setEnable(id, enb) {
	var x = document.getElementById(id);
	x.className = (enb ? "show" : "hide");
	return enb;
}

function hasAdminRole() {
	return (username != "") &&
				(check(tomcat.admin) ||
					((currentservice < storage.length) && check(storage[currentservice].admin)) ||
						check(query.admin) ||
							check(file.admin));
}

function hasAuthorRole() {
	return (username != "") &&
				(currentservice < storage.length) &&
					check(storage[currentservice].author);
}

function hasPublisherRole() {
	return (username != "") &&
				(currentservice < storage.length) &&
					check(storage[currentservice].publisher);
}

function check(rolename) {
	if (rolename == null) return true;
	for (var i=0; i<roles.length; i++) {
		if (roles[i] == rolename) return true;
	}
	return false;
}

function setStatusLine(text) {
	window.status = text;
}
