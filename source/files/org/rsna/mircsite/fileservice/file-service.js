var fileItems = new Array(
		new Item("New folder", newFolder, "newfolder"),
		new Item("Rename folder", renameFolder, "renamefolder"),
		new Item("Delete folder", deleteFolder, "deletefolder"),
		new Item("", null),
		new Item("Upload file", uploadFile, "uploadfile"),
		new Item("Rename file", renameFile, "renamefile"),
		new Item("Delete file(s)", deleteFiles, "deletefiles"),
		new Item("Export file(s)", exportFiles, "exportfiles"),
		new Item("", null),
		new Item("Close", closeHandler) );

var confItems = new Array(
		new Item("Show normal titles", normalTitles, "normaltitles"),
		new Item("Show unknown titles", unknownTitles, "unknowntitles"),
		new Item("", null),
		new Item("New conference", newConference, "newconference"),
		new Item("Rename conference", renameConference, "renameconference"),
		new Item("Delete conference", deleteConference, "deleteconference"),
		new Item("", null),
		new Item("Delete agenda items", deleteAgendaItems, "deleteagendaitems") );

var editItems = new Array(
		new Item("Select all", selectAll, "selectall"),
		new Item("Deselect all", deselectAll, "deselectall") );

var helpItems = new Array (
		new Item("MIRC Wiki", showWiki) );

var fileMenu = new Menu("File", fileItems, "filemenu");
var confMenu = new Menu("Conferences", confItems, "confmenu");
var editMenu = new Menu("Edit", editItems, "editmenu");
var helpMenu = new Menu("Help", helpItems);

var menuBar = new MenuBar("menuBar", new Array (fileMenu, confMenu, editMenu, helpMenu));
var treeManager;

window.onload = load;
window.onresize = doResize;

var currentNode = null;
var currentPath = "";
var lastClicked = -1;
var nFiles = 0;
var nodeType = null;
var nAgendaItems = 0;
var nChildConferences = 0;
var showNormalTitles = (getCookie("showNormalTitles") != "NO");
var closeboxURL = "/mirc/images/closebox.gif";
var split;

function doResize() {
	resize();
	split.positionSlider();
}

function load() {
	setPageHeader("File Service", username, closeboxURL, closeHandler);
	menuBar.display();
	split = new HorizontalSplit("left", "center", "right", true);
	doResize();
	treeManager =
		new TreeManager(
			"left",
			"/file/service/tree",
			"/mirc/images/plus.gif",
			"/mirc/images/minus.gif");
	treeManager.load();
	treeManager.display();
	if (openpath != "") currentNode = treeManager.expandPath(openpath);
	else treeManager.expandAll();
	if (currentNode != null) {
		treeManager.closePaths();
		currentNode.showPath();
		showCurrentFileDirContents();
	};
	setEnables();
}

function isRootFolder() {
	return (currentPath == "Shared/Files") || (currentPath == "Personal/Files");
}
function isSharedFolder(path) {
	return (path.indexOf("Shared/Files") == 0);
}
function isPersonalFolder(path) {
	return (path.indexOf("Personal/Files") == 0);
}
function isRootConference() {
	return (currentPath == "Shared/Conferences") || (currentPath == "Personal/Conferences");
}
function isSharedConference(path) {
	return (path.indexOf("Shared/Conferences") == 0);
}
function isPersonalConference(path) {
	return (path.indexOf("Personal/Conferences") == 0);
}

function setEnables() {
	var isMircFolder = (nodeType=="MircFolder");
	var isConference = (nodeType=="Conference");

	var nSelected = isMircFolder ? getSelectedCount() : getSelectedAICount();
	var nChildDirs = isMircFolder&&currentNode ? currentNode.trees.length : -1;
	menuBar.setEnable("newfolder", isMircFolder);
	menuBar.setEnable("deletefolder", isMircFolder && !isRootFolder() /*&& (nFiles == 0) && (nChildDirs == 0)*/);
	menuBar.setEnable("renamefolder", isMircFolder && !isRootFolder());
	menuBar.setEnable("uploadfile", isMircFolder);
	menuBar.setEnable("renamefile", isMircFolder && (nSelected == 1));
	menuBar.setEnable("deletefiles", isMircFolder && (nSelected > 0));
	menuBar.setEnable("exportfiles", isMircFolder && (nSelected > 0));

	menuBar.setEnable("newconference", isConference);
	menuBar.setEnable("deleteconference", isConference && !isRootConference() /*&& (nAgendaItems == 0) && (nChildConferences == 0)*/);
	menuBar.setEnable("renameconference", isConference && !isRootConference());
	menuBar.setEnable("deleteagendaitems", isConference && (nSelected > 0));

	menuBar.setEnable("selectall", isMircFolder && (nFiles > 0));
	menuBar.setEnable("deselectall", isMircFolder && (nSelected > 0));

	menuBar.setEnable("normaltitles", !showNormalTitles);
	menuBar.setEnable("unknowntitles", showNormalTitles);
}

//Handlers for tree selection
//
function showFileDirContents(event) {
	var source = getSource(getEvent(event));
	currentNode = source.treenode;
	showCurrentFileDirContents();
}

function showCurrentFileDirContents() {
	currentPath = currentNode.getPath();
	treeManager.closePaths();
	currentNode.showPath();
	menuBar.setText(currentPath);
	var req = new AJAX();
	req.GET("/file/service/mirc/"+currentPath, req.timeStamp(), null);
	if (req.success()) {
		var right = document.getElementById("right");
		while (right.firstChild) right.removeChild(right.firstChild);
		nFiles = 0;
		var xml = req.responseXML();
		var root = xml ? xml.firstChild : null;
		var child = root ? root.firstChild : null;
		while (child) {
			if ((child.nodeType == 1) && (child.tagName == "file")) {
				var img = document.createElement("IMG");
				img.className = "desel";
				//img.onclick = cabinetFileClicked;
				img.ondblclick = cabinetFileDblClicked;
				img.onmousedown = startImageDrag;
				img.setAttribute("src", "/file/service/"+child.getAttribute("iconURL"));
				img.setAttribute("title", child.getAttribute("title"));
				img.xml = child;
				right.appendChild(img);
				nFiles++;
			}
			child = child.nextSibling;
		}
		lastClicked = -1;
		nodeType = "MircFolder";
		nSelected = 0;
	}
	else alert("The attempt to get the directory contents failed.");
	setEnables();
}

function showMyRsnaDirContents(event) {
	var source = getSource(getEvent(event));
	currentNode = source.treenode;
	currentPath = currentNode.getPath();
	treeManager.closePaths();
	currentNode.showPath();
	menuBar.setText(currentPath);
	var req = new AJAX();
	req.GET("/file/service/myrsna/folder/"+currentNode.nodeID, req.timeStamp(), null);
	if (req.success()) {
		var right = document.getElementById("right");
		while (right.firstChild) right.removeChild(right.firstChild);
		nFiles = 0;
		var xml = req.responseXML();
		var root = xml ? xml.firstChild : null;
		var child = root ? root.firstChild : null;
		while (child) {
			if ((child.nodeType == 1) && (child.tagName == "file")) {
				var img = document.createElement("IMG");
				img.className = "desel";
				img.src = child.getAttribute("iconURL");
				img.title = child.getAttribute("title");
				img.xml = child;
/*
				img.onclick = cabinetFileClicked;
				img.ondblclick = cabinetFileDblClicked;
				img.ondragstart = startImageDrag;
*/
				right.appendChild(img);
				nFiles++;
			}
			child = child.nextSibling;
		}
		lastClicked = -1;
		nodeType = "MyRsnaFolder";
		nSelected = 0;
	}
	else alert("The attempt to get the directory contents failed.");
	setEnables();
}

function showConferenceContents(event) {
	var source = getSource(getEvent(event));
	currentNode = source.treenode;
	showCurrentConferenceContents();
}

function showCurrentConferenceContents() {
	currentPath = currentNode.getPath();
	treeManager.closePaths();
	currentNode.showPath();
	menuBar.setText(currentPath);
	nodeType = "Conference";
	var req = new AJAX();
	req.GET("/file/service/getAgenda", "nodeID="+currentNode.nodeID+"&"+req.timeStamp(), null);
	if (req.success()) {
		var right = document.getElementById("right");
		while (right.firstChild) right.removeChild(right.firstChild);
		nFiles = 0;
		var xml = req.responseXML();
		var root = xml ? xml.firstChild : null;
		var child = root ? root.firstChild : null;
		while (child) {
			if ((child.nodeType == 1) && (child.tagName == "item")) {
				right.appendChild(
						makeItemTable(
								child.getAttribute("url"),
								child.getAttribute("title"),
								child.getAttribute("alturl"),
								child.getAttribute("alttitle"),
								child.getAttribute("subtitle") ) );
			}
			child = child.nextSibling;
		}
		lastClicked = -1;
		nodeType = "Conference";
		nSelected = 0;
	}
	else alert("The attempt to get the conference agenda failed. [req.status="+req.status+"]");
	setEnables();
}

function makeItemTable(url, title, alturl, alttitle, subtitle) {
	var div = document.createElement("DIV");
	div.className = "agenda";
	div.url = url;
	var table = document.createElement("TABLE");
	table.className = "AIdesel"
	var tbody = document.createElement("TBODY");
	var tr = document.createElement("TR");
	var td = document.createElement("TD");
	td.className = "AIbullet";
	var bullet = document.createElement("IMG");
	//bullet.onclick = agendaItemClicked;
	bullet.onmousedown = startAgendaItemDrag;

	bullet.src = "/mirc/images/bullet.gif";
	bullet.url = url;
	td.appendChild(bullet);
	tr.appendChild(td);
	td = document.createElement("TD");
	td.className = "agendaitem";
	var anchor = document.createElement("A");
	anchor.target = "agendaitem";
	if (showNormalTitles) {
		anchor.href = url;
		anchor.appendChild(document.createTextNode(title));
	}
	else {
		anchor.href = alturl;
		anchor.appendChild(document.createTextNode(alttitle));
	}
	td.appendChild(anchor);
	if (subtitle != "") {
		td.appendChild(document.createElement("BR"));
		td.appendChild(document.createTextNode("\u00A0\u00A0\u00A0\u00A0" + subtitle));
	}
	tr.appendChild(td);
	tbody.appendChild(tr);
	table.appendChild(tbody);
	div.appendChild(table);
	return div;
}

//Handlers for MIRC file cabinet selection in the right pane
//
function cabinetFileClicked(theEvent) {
	theEvent = getEvent(theEvent)
	stopEvent(theEvent);
	var source = getSource(theEvent);
	var currentClicked = getClicked(source);
	if (currentClicked == -1) return;

	if (theEvent.altKey) {
		var filename = source.getAttribute("title");
		if ((filename.toLowerCase().lastIndexOf(".dcm") == filename.length - 4) ||
					(filename.replace(/[\.\d]/g,"").length == 0)) {
			deselectAll();
			source.className = "sel";
			lastClicked = currentClicked;
			var path = "/file/service/"+currentPath+"/"+filename+"?list";
			window.open(path,"_blank");
		}
	}

	else if (!theEvent.shiftKey && !theEvent.ctrlKey) {
		deselectAll();
		source.className = "sel";
		lastClicked = currentClicked;
	}

	else if (getEvent(theEvent).ctrlKey) {
		if (source.className == "sel") source.className = "desel";
		else source.className ="sel";
		lastClicked = currentClicked;
	}

	else {
		if (lastClicked == -1) {
			lastClicked = currentClicked;
			source.className = "sel";
		}
		else {
			var files = getCabinetFiles();
			var start = Math.min(lastClicked,currentClicked);
			var stop = Math.max(lastClicked,currentClicked);
			for (var i=start; i<=stop; i++) files[i].className = "sel";
		}
	}
	setEnables();
}

function cabinetFileDblClicked(theEvent) {
	var theEvent = getEvent(theEvent)
	stopEvent(theEvent);
	var source = getSource(theEvent);
	var currentClicked = getClicked(source);
	if (currentClicked == -1) return;
	var filename = source.getAttribute("title");
	deselectAll();
	source.className = "sel";
	lastClicked = currentClicked;
	var path = "/file/service/"+currentPath+"/"+filename;
	window.open(path,"_blank");
	setEnables();
}

function getClicked(file) {
	var files = getCabinetFiles();
	for (var i=0; i<files.length; i++) {
		if (file === files[i]) {
			return i;
		}
	}
	return -1;
}

function getSelected() {
	var files = getCabinetFiles();
	var list = "";
	for (var i=0; i<files.length; i++) {
		if (files[i].className == "sel") {
			if (list != "") list += "|";
			list += files[i].getAttribute("title");
		}
	}
	return list;
}

function getSelectedCount() {
	var files = getCabinetFiles();
	var n = "";
	for (var i=0; i<files.length; i++) {
		if (files[i].className == "sel") n++;
	}
	return n;
}

function getCabinetFiles() {
	var right = document.getElementById("right");
	return right.getElementsByTagName("IMG");
}

//Handlers for Agenda Item selection in the right pane
//
function agendaItemClicked(theEvent) {
	theEvent = getEvent(theEvent)
	stopEvent(theEvent);
	var source = getSource(theEvent);
	var ai = source.parentNode;
	while ((ai != null) && (ai.tagName != "TABLE")) ai = ai.parentNode;

	var currentAIClicked = getAIClicked(ai);
	if (currentAIClicked == -1) return;

	if (!theEvent.shiftKey && !theEvent.ctrlKey) {
		deselectAllAI();
		ai.className = "AIsel";
		lastClicked = currentAIClicked;
	}

	else if (getEvent(theEvent).ctrlKey) {
		if (ai.className == "AIsel") ai.className = "AIdesel";
		else ai.className ="AIsel";
		lastClicked = currentAIClicked;
	}

	else {
		if (lastClicked == -1) {
			lastClicked = currentAIClicked;
			ai.className = "AIsel";
		}
		else {
			var ais = getAIs();
			var start = Math.min(lastClicked,currentAIClicked);
			var stop = Math.max(lastClicked,currentAIClicked);
			for (var i=start; i<=stop; i++) ais[i].className = "AIsel";
		}
	}
	setEnables();
}

function getAIs() {
	var right = document.getElementById("right");
	return right.getElementsByTagName("TABLE");
}

function getAIClicked(ai) {
	var ais = getAIs();
	for (var i=0; i<ais.length; i++) {
		if (ai === ais[i]) {
			return i;
		}
	}
	return -1;
}

function deselectAllAI() {
	var ais = getAIs();
	for (var i=0; i<ais.length; i++) ais[i].className = "AIdesel";
	setEnables();
}

function getSelectedAICount() {
	var ais = getAIs();
	var n = 0;
	for (var i=0; i<ais.length; i++) {
		if (ais[i].className == "AIsel") n++;
	}
	return n;
}

function getSelectedAIs() {
	var ais = getAIs();
	var list = "";
	for (var i=0; i<ais.length; i++) {
		var bullet = ais[i].getElementsByTagName("IMG");
		var url = bullet[0].url;
		if (ais[i].className == "AIsel") {
			if (list != "") list += "|";
			list += url;
		}
	}
	return list;
}

function deleteAgendaItems() {
	var div = document.getElementById("ediv");
	if (div) div.parentNode.removeChild(div);
	if (getSelectedAICount() > 0) {
		showTextDialog("ediv", 400, 210, "Are you sure?", closeboxURL, "Delete?",
			"Are you sure you want to delete the selected agenda items?",
			deleteAgendaItemsHandler, hidePopups);
	}
}

function deleteAgendaItemsHandler() {
	hidePopups();
	var selected = getSelectedAIs();
	if (selected != "") {
		selected = encodeURIComponent(selected);
		var req = new AJAX();
		req.GET("/file/service/deleteAgendaItems", "nodeID="+currentNode.nodeID+"&list="+selected+"&"+req.timeStamp(), null);
		if (req.success()) {
			var xml = req.responseXML();
			var root = xml ? xml.firstChild : null;
			if ((root != null) && (root.tagName == "ok")) {
				showCurrentConferenceContents();
			}
		}
		else alert("The attempt to delete the agenda items failed.");
	}
}


//Handlers for the File Menu
//
function newFolder() {
	var div = document.getElementById("ediv");
	if (div) div.parentNode.removeChild(div);

	var div = document.createElement("DIV");
	div.className = "content";
	var p = document.createElement("P");
	p.className = "centeredblock";
	p.appendChild(document.createTextNode("Folder name:\u00A0"));
	var text = document.createElement("INPUT");
	text.id = "etext1";
	text.className = "textbox";
	p.appendChild(text);
	div.appendChild(p);

	showDialog("ediv", 400, 200, "New Folder", closeboxURL, "Create New Folder", div, doNewFolder, hidePopups);
	window.setTimeout("document.getElementById('etext1').focus()", 500);
}
function doNewFolder(event) {
	var name = document.getElementById("etext1").value;
	var div = document.getElementById("ediv");
	if (div) div.parentNode.removeChild(div);
	name = trim(name);
	if (name.length == 0) return;
	name = name.replace(/\s+/g,"_");
	var req = new AJAX();
	req.GET("/file/service/createFolder/"+currentPath+"/"+name, req.timeStamp(), null);
	if (req.success()) {
		var xml = req.responseXML();
		var root = xml ? xml.firstChild : null;
		var child = root ? root.firstChild : null;
		var tree = new Tree(currentNode.treeManager, currentNode.parent, currentNode.level);
		tree.load(child);
		var state = treeManager.getState();
		currentNode = currentNode.parent.replaceTree(currentNode, tree);
		treeManager.display(state);
		currentNode.expand();
		currentNode.showPath();
	}
	else alert("The attempt to create the directory failed.");
}

function renameFolder() {
	var div = document.getElementById("ediv");
	if (div) div.parentNode.removeChild(div);

	var div = document.createElement("DIV");
	div.className = "content";
	var p = document.createElement("P");
	p.className = "centeredblock";
	p.appendChild(document.createTextNode("New name:\u00A0"));
	var text = document.createElement("INPUT");
	text.id = "etext1";
	text.className = "textbox";
	p.appendChild(text);
	div.appendChild(p);

	showDialog("ediv", 400, 200, "Rename Folder", closeboxURL, "Rename Folder", div, doRenameFolder, hidePopups);
	window.setTimeout("document.getElementById('etext1').focus()", 500);
}
function doRenameFolder(event) {
	var name = document.getElementById("etext1").value;
	var div = document.getElementById("ediv");
	if (div) div.parentNode.removeChild(div);
	name = trim(name);
	if (name.length == 0) return;
	name = name.replace(/\s+/g,"_");
	var req = new AJAX();
	req.GET("/file/service/renameFolder/"+currentPath, "newname="+name+"&"+req.timeStamp(), null);
	if (req.success()) {
		var xml = req.responseXML();
		var root = xml ? xml.firstChild : null;
		if ((root != null) && (root.tagName == "ok")) {
			currentNode.name = name;
			var state = treeManager.getState();
			treeManager.display(state);
			currentNode.showPath();
			return;
		}
	}
	alert("The attempt to rename the directory failed.");
}

function renameFile() {
	var div = document.getElementById("ediv");
	if (div) div.parentNode.removeChild(div);

	var div = document.createElement("DIV");
	div.className = "content";
	var p = document.createElement("P");
	p.className = "centeredblock";
	p.appendChild(document.createTextNode("New name:\u00A0"));
	var text = document.createElement("INPUT");
	text.id = "etext1";
	text.className = "textbox";
	p.appendChild(text);
	div.appendChild(p);

	showDialog("ediv", 400, 200, "Rename File", closeboxURL, "Rename File", div, doRenameFile, hidePopups);
	window.setTimeout("document.getElementById('etext1').focus()", 500);
}
function doRenameFile(event) {
	var name = document.getElementById("etext1").value;
	var div = document.getElementById("ediv");
	if (div) div.parentNode.removeChild(div);
	name = trim(name);
	if (name.length == 0) return;
	if (getSelectedCount() != 1) return;
	var currentName = getSelected();
	name = name.replace(/\s+/g,"_");
	var req = new AJAX();
	req.GET("/file/service/renameFile/"+currentPath,"oldname="+currentName+"&newname="+name+"&"+req.timeStamp(), null);
	if (req.success()) showCurrentFileDirContents();
	else alert("The attempt to rename the file failed.");
}

function deleteFolder() {
	var div = document.getElementById("ediv");
	if (div) div.parentNode.removeChild(div);
	showTextDialog("ediv", 400, 210, "Are you sure?", closeboxURL, "Delete?",
		"Are you sure you want to delete the selected folder and all its files and subfolders?",
		deleteFolderHandler, hidePopups);
}

function deleteFolderHandler() {
	hidePopups();
	var req = new AJAX();
	req.GET("/file/service/deleteFolder/"+currentPath, req.timeStamp(), null);
	if (req.success()) {
		var xml = req.responseXML();
		var root = xml ? xml.firstChild : null;
		var child = root ? root.firstChild : null;
		var parent = currentNode.parent;
		var tree = new Tree(parent.treeManager, parent.parent, parent.level);
		tree.load(child);
		var state = treeManager.getState();
		currentNode = currentNode.parent.parent.replaceTree(currentNode.parent, tree);
		treeManager.display(state);
		currentNode.expand();
		showCurrentFileDirContents();
		currentNode.showPath();
	}
	else alert("The attempt to delete the directory failed.");
}

function uploadFile() {
	var div = document.getElementById("ediv");
	if (div) div.parentNode.removeChild(div);

	var div = document.createElement("DIV");
	div.className = "content";

	var form = document.createElement("FORM");
	form.method = "post";
	form.target = "_self";
	form.encoding = "multipart/form-data";
	form.acceptCharset = "UTF-8";
	form.action = "/file/service/uploadFile/"+currentPath;
	div.appendChild(form);

	var p = document.createElement("P");
	p.className = "centeredblock";
	p.appendChild(document.createTextNode("Upload to: "));
	p.appendChild(document.createTextNode(currentPath));
	form.appendChild(p);

	var input = document.createElement("INPUT");
	input.style.width = "80%";
	input.name = "filecontent";
	input.id = "selectedFile";
	input.type = "file";
	form.appendChild(input);

	form.appendChild(document.createElement("BR"));
	form.appendChild(document.createElement("BR"));

	var cb = document.createElement("INPUT");
	cb.name = "anonymize";
	cb.type = "checkbox";
	cb.value = "yes";
	form.appendChild(cb);
	form.appendChild(document.createTextNode(" Anonymize DICOM Files"));

	form.appendChild(document.createElement("BR"));
	form.appendChild(document.createElement("BR"));

	var submit = document.createElement("INPUT");
	submit.type = "submit";
	submit.value = "Submit File";
	form.appendChild(submit);

	showDialog("ediv", 475, 270, "Upload File", closeboxURL, "Upload File", div);
}

function deleteFiles() {
	var div = document.getElementById("ediv");
	if (div) div.parentNode.removeChild(div);
	if (getSelectedCount() > 0) {
		showTextDialog("ediv", 400, 195, "Are you sure?", closeboxURL, "Delete?",
			"Are you sure you want to delete the selected files?",
			deleteFilesHandler, hidePopups);
	}
}

function deleteFilesHandler() {
	hidePopups();
	var selected = getSelected();
	if (selected != "") {
		selected = encodeURIComponent(selected);
		var req = new AJAX();
		req.GET("/file/service/deleteFiles/"+currentPath, "list="+selected+"&"+req.timeStamp(), null);
		if (req.success()) {
			showCurrentFileDirContents();
		}
		else alert("The attempt to delete the files failed.");
	}
}

function exportFiles() {
	var selected = getSelected();
	if (selected != "") {
		var ts = new AJAX().timeStamp();
		selected = encodeURIComponent(selected);
		window.open("/file/service/exportFiles/"+currentPath+"?list="+selected+"&"+ts,"_self");
	}
}

function trim(text) {
	if (text == null) return "";
	text = text.replace( /^\s+/g, "" );// strip leading
	return text.replace( /\s+$/g, "" );// strip trailing
}

//Handlers for the Conferences Menu
function normalTitles() {
	showNormalTitles = true;
	setCookie("showNormalTitles", "YES");
	setEnables();
	if (nodeType == "Conference") showCurrentConferenceContents();
}
function unknownTitles() {
	showNormalTitles = false;
	setCookie("showNormalTitles", "NO");
	setEnables();
	if (nodeType == "Conference") showCurrentConferenceContents();
}

function newConference() {
	var div = document.getElementById("ediv");
	if (div) div.parentNode.removeChild(div);

	var div = document.createElement("DIV");
	div.className = "content";
	var p = document.createElement("P");
	p.className = "centeredblock";
	p.appendChild(document.createTextNode("Conference name:\u00A0"));
	var text = document.createElement("INPUT");
	text.id = "etext1";
	text.className = "textbox";
	p.appendChild(text);
	div.appendChild(p);

	showDialog("ediv", 400, 200, "New Conference", closeboxURL, "Create New Conference", div, doNewConference, hidePopups);
	window.setTimeout("document.getElementById('etext1').focus()", 500);
}
function doNewConference(event) {
	var name = document.getElementById("etext1").value;
	var div = document.getElementById("ediv");
	if (div) div.parentNode.removeChild(div);
	name = trim(name);
	if (name.length == 0) return;
	name = name.replace(/\s+/g,"_");
	var req = new AJAX();
	var qs = "id="+currentNode.nodeID+"&name="+encodeURIComponent(name)+"&"+req.timeStamp();
	req.GET("/file/service/createConference", qs, null);
	if (req.success()) {
		var xml = req.responseXML();
		var root = xml ? xml.firstChild : null;
		var child = root ? root.firstChild : null;
		var tree = new Tree(currentNode.treeManager, currentNode.parent, currentNode.level);
		tree.load(child);
		var state = treeManager.getState();
		currentNode = currentNode.parent.replaceTree(currentNode, tree);
		treeManager.display(state);
		currentNode.expand();
		currentNode.showPath();
	}
	else alert("The attempt to create the conference failed.");
}

function renameConference() {
	var div = document.getElementById("ediv");
	if (div) div.parentNode.removeChild(div);

	var div = document.createElement("DIV");
	div.className = "content";
	var p = document.createElement("P");
	p.className = "centeredblock";
	p.appendChild(document.createTextNode("New name:\u00A0"));
	var text = document.createElement("INPUT");
	text.id = "etext1";
	text.className = "textbox";
	p.appendChild(text);
	div.appendChild(p);

	showDialog("ediv", 400, 200, "Rename Conference", closeboxURL, "Rename Conference", div, doRenameConference, hidePopups);
	window.setTimeout("document.getElementById('etext1').focus()", 500);
}
function doRenameConference(event) {
	var name = document.getElementById("etext1").value;
	var div = document.getElementById("ediv");
	if (div) div.parentNode.removeChild(div);
	name = trim(name);
	if (name.length == 0) return;
	name = name.replace(/\s+/g,"_");
	var req = new AJAX();
	var qs = "id="+currentNode.nodeID+"&name="+encodeURIComponent(name)+"&"+req.timeStamp();
	req.GET("/file/service/renameConference", qs, null);
	if (req.success()) {
		var xml = req.responseXML();
		var root = xml ? xml.firstChild : null;
		if ((root != null) && (root.tagName == "ok")) {
			currentNode.name = name;
			var state = treeManager.getState();
			treeManager.display(state);
			currentNode.showPath();
			return;
		}
	}
	alert("The attempt to rename the conference failed.");
}

function deleteConference() {
	var div = document.getElementById("ediv");
	if (div) div.parentNode.removeChild(div);
	showTextDialog("ediv", 400, 210, "Are you sure?", closeboxURL, "Delete?",
		"Are you sure you want to delete the selected conference and all its subconferences?",
		deleteConferenceHandler, hidePopups);
}

function deleteConferenceHandler() {
	hidePopups();
	var req = new AJAX();
	var qs = "id="+currentNode.nodeID+"&"+req.timeStamp();
	req.GET("/file/service/deleteConference", qs, null);
	if (req.success()) {
		var xml = req.responseXML();
		var root = xml ? xml.firstChild : null;
		var child = root ? root.firstChild : null;
		var parent = currentNode.parent;
		var tree = new Tree(parent.treeManager, parent.parent, parent.level);
		tree.load(child);
		var state = treeManager.getState();
		currentNode = currentNode.parent.parent.replaceTree(currentNode.parent, tree);
		treeManager.display(state);
		currentNode.expand();
		showCurrentConferenceContents();
		currentNode.showPath();
	}
	else alert("The attempt to delete the conference failed. req.status="+req.status);
}


//Handlers for the Edit Menu
//
function selectAll() {
	if (nodeType == "MircFolder") {
		var files = getCabinetFiles();
		for (var i=0; i<files.length; i++) files[i].className = "sel";
		setEnables();
	}
}

function deselectAll() {
	if (nodeType == "MircFolder") {
		var files = getCabinetFiles();
		for (var i=0; i<files.length; i++) files[i].className = "desel";
		setEnables();
	}
}

//Handler for closing the page
//
function closeHandler() {
	hidePopups();
	window.open("/mirc/query","_self");
}

//Handlers for the Help menu
//
function showWiki(event, item) {
	window.open("http://mircwiki.rsna.org/index.php?title=Main_Page","help");
}

//**************************************
//**** Drag/drop handler for images ****
//**************************************

//This handler starts the drag of selected images.
function startImageDrag(event) {
	event = getEvent(event);
	var right = document.getElementById("right");
	var scrollTop = right.scrollTop;
	var node = getSource(event);
	var list = new Array();
	list[list.length] = getDragableImgDiv(node, event.clientX, event.clientY, scrollTop);
	var files = right.getElementsByTagName("IMG");
	for (var i=0; i<files.length; i++) {
		if ((files[i].className == "sel") && !(files[i] === node)) {
			list[list.length] = getDragableImgDiv(files[i], event.clientX, event.clientY, scrollTop);
		}
	}
	dragImage(list, event);
	cabinetFileClicked(event);
}

//Make a div containing an image to drag.
function getDragableImgDiv(node, clientX, clientY, scrollTop) {
	var pos = findObject(node);
	setStatusLine(pos.y + "; " + scrollTop);
	var div = document.createElement("DIV");
	div.deltaX = clientX - parseInt(pos.x);
	div.deltaY = clientY - parseInt(pos.y);
	div.style.position = "absolute";
	div.style.width = pos.w;
	div.style.height = pos.h;
	div.style.left = pos.x;
	div.style.top = pos.y - scrollTop;
	div.style.visibility = "visible";
	div.style.display = "block";
	div.style.overflow = "hidden";
	div.style.zIndex = 5;
	div.style.backgroundColor = "transparent";
	div.style.filter = "alpha(opacity=40, style=0)";
	div.parentScrollTop = scrollTop;
	var img = document.createElement("IMG");
	img.src = node.src;
	div.appendChild(img);
	div.title = node.title;
	document.body.appendChild(div);
	return div;
}

//Drag and drop an image
function dragImage(list, event) {
	var node = list[0];
	if (document.addEventListener) {
		document.addEventListener("mousemove", dragIt, false);
		document.addEventListener("mouseup", dropIt, false);
	}
	else {
		node.attachEvent("onmousemove", dragIt);
		node.attachEvent("onmouseup", dropIt);
		node.setCapture();
	}
	if (event.stopPropagation) event.stopPropagation();
	else event.cancelBubble = true;
	if (event.preventDefault) event.preventDefault();
	else event.returnValue = false;

	function dragIt(evt) {
		if (!evt) evt = window.event;
		for (var i=0; i<list.length; i++) {
			list[i].style.left = (evt.clientX - list[i].deltaX) + "px";
			list[i].style.top = (evt.clientY - list[i].deltaY - list[i].parentScrollTop) + "px";
		}
		if (evt.stopPropagation) event.stopPropagation();
		else evt.cancelBubble = true;
	}

	function dropIt(evt) {
		if (!evt) evt = window.event;
		handleDrop(evt);
		if (document.addEventListener) {
			document.removeEventListener("mouseup", dropIt, true);
			document.removeEventListener("mousemove", dragIt, true);
		}
		else {
			node.detachEvent("onmousemove", dragIt);
			node.detachEvent("onmouseup", dropIt);
			node.releaseCapture();
		}
		if (evt.stopPropagation) event.stopPropagation();
		else evt.cancelBubble = true;
	}

	function handleDrop(evt) {
		var files = "";
		for (var i=0; i<list.length; i++) {
			if (files != "") files += "|";
			files += list[i].title;
			document.body.removeChild(list[i]);
		}
		var left = document.getElementById("left");
		var scrollTop = left.scrollTop;
		var pos = findObject(left);
		if (evt.clientX > pos.w) return false;
		var sourcePath = currentPath;
		var destTree = treeManager.getTreeForCoords(evt.clientX, evt.clientY + scrollTop);
		if (destTree) {
			var destPath = destTree ? destTree.getPath() : "null";
			if (isSharedFolder(destPath) || isPersonalFolder(destPath)) {
				var req = new AJAX();
				var qs = "sourcePath="+sourcePath+"&destPath="+destPath+"&files="+files+"&"+req.timeStamp();
				req.GET("/file/service/copyFiles", qs, null);
				if (req.success()) {
					//currentNode = destTree;
					//showCurrentFileDirContents();
					alert("The files were copied successfully to\n"+destPath);
				}
				else alert("The attempt to copy the files failed.");
			}
		}
	}
}

//********************************************
//**** Drag/drop handler for agenda items ****
//********************************************

//This handler starts the drag of selected agenda items.
function startAgendaItemDrag(event) {
	event = getEvent(event);
	var right = document.getElementById("right");
	var scrollTop = right.scrollTop;
	var node = getSource(event);
	var url = node.url;

	agendaItemClicked(event);

	while (node.tagName != "TABLE") node = node.parentNode;
	if ((getSelectedAICount() == 1) && (node.className == "AIsel")) {
		var dragableAIDiv = getDragableAIDiv(node.parentNode, event.clientX, event.clientY, scrollTop);
		dragableAIDiv.url = url;
		dragAgendaItem(dragableAIDiv, event);
	}
}

//Make a div containing an agenda item to drag.
function getDragableAIDiv(node, clientX, clientY, scrollTop) {
	var pos = findObject(node);
	var div = document.createElement("DIV");
	div.deltaX = clientX - parseInt(pos.x);
	div.deltaY = clientY - parseInt(pos.y);
	div.style.position = "absolute";
	div.style.width = pos.w;
	div.style.height = pos.h;
	div.style.left = pos.x;
	div.style.top = pos.y - scrollTop;
	div.style.visibility = "visible";
	div.style.display = "block";
	div.style.overflow = "hidden";
	div.style.zIndex = 5;
	div.style.backgroundColor = "transparent";
	div.style.filter = "alpha(opacity=75, style=0)";
	div.parentScrollTop = scrollTop;
	var table = node.firstChild.cloneNode(true);
	table.className = "AIsel";
	div.appendChild(table);
	document.body.appendChild(div);
	return div;
}

//Drag and drop agenda items
function dragAgendaItem(dragableAIDiv, event) {
	var node = dragableAIDiv;
	if (document.addEventListener) {
		document.addEventListener("mousemove", dragIt, false);
		document.addEventListener("mouseup", dropIt, false);
	}
	else {
		node.attachEvent("onmousemove", dragIt);
		node.attachEvent("onmouseup", dropIt);
		node.setCapture();
	}
	if (event.stopPropagation) event.stopPropagation();
	else event.cancelBubble = true;
	if (event.preventDefault) event.preventDefault();
	else event.returnValue = false;

	function dragIt(evt) {
		if (!evt) evt = window.event;
		node.style.left = (evt.clientX - node.deltaX) + "px";
		node.style.top = (evt.clientY - node.deltaY - node.parentScrollTop) + "px";
		if (evt.stopPropagation) event.stopPropagation();
		else evt.cancelBubble = true;
	}

	function dropIt(evt) {
		if (!evt) evt = window.event;
		handleDrop(evt);
		if (document.addEventListener) {
			document.removeEventListener("mouseup", dropIt, true);
			document.removeEventListener("mousemove", dragIt, true);
		}
		else {
			node.detachEvent("onmousemove", dragIt);
			node.detachEvent("onmouseup", dropIt);
			node.releaseCapture();
		}
		if (evt.stopPropagation) event.stopPropagation();
		else evt.cancelBubble = true;
	}

	function handleDrop(evt) {
		document.body.removeChild(node);

		var left = document.getElementById("left");
		var scrollTop = left.scrollTop;
		var pos = findObject(left);
		if (evt.clientX > pos.w) {
			//This is a rearrangement within the conference
			var right = document.getElementById("right");
			var ais = right.getElementsByTagName("DIV");
			var skipNext = false;
			for (var i=0; i<ais.length; i++) {
				if (ais[i].url != node.url) {
					if (!skipNext) {
						var aiPos = findObject(ais[i]);
						if ((evt.clientY >= aiPos.y) && (evt.clientY < aiPos.y + aiPos.h)) {
							var req = new AJAX();
							var qs = "nodeID="+currentNode.nodeID
										+"&sourceURL="+encodeURIComponent(node.url)
											+"&targetURL="+encodeURIComponent(ais[i].url)
												+"&"+req.timeStamp();
							req.GET("/file/service/moveAgendaItem", qs, null);
							if (req.success()) showCurrentConferenceContents()
							else alert("The attempt to move the agenda item failed.");
							return false;
						}
					}
					skipNext = false;
				}
				else skipNext = true;
			}
		}
		else {
			//This is a move to another conference (maybe)
			var sourcePath = currentPath;
			var destTree = treeManager.getTreeForCoords(evt.clientX, evt.clientY + scrollTop);
			if (destTree) {
				var destPath = destTree ? destTree.getPath() : "null";
				if (isSharedConference(destPath) || isPersonalConference(destPath)) {
					var req = new AJAX();

					var qs = "sourceID="+currentNode.nodeID
								+"&targetID="+destTree.nodeID
									+"&url="+encodeURIComponent(node.url)
										+"&"+req.timeStamp();

					req.GET("/file/service/transferAgendaItem", qs, null);
					if (req.success()) showCurrentConferenceContents()
					else alert("The attempt to transfer the agenda item failed.");
				}
			}
		}
	}
}
