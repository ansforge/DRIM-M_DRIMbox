/*
 *  XdmAPI.java - DRIMBox
 *
 * N°IDDN : IDDN.FR.001.020012.000.S.C.2023.000.30000
 *
 * MIT License
 *
 * Copyright (c) 2022 b<>com
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.bcom.drimbox.xdm;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.lang3.StringUtils;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.DicomInputStream.IncludeBulkData;
import org.dcm4che3.util.UIDUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.bcom.drimbox.dmp.database.DatabaseManager;
import com.bcom.drimbox.dmp.database.SourceEntity;
import com.bcom.drimbox.utils.XMLUtils;

import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import nu.xom.Node;

@Singleton
@Path("/api/source")
public class XdmAPI {

	@Inject
	DatabaseManager databaseManager;

	@ConfigProperty(name = "xdm.path")
	String xdmPath;

	@GET
	@Path("/xdmExport")
	public void exportXDM() {


		PanacheQuery<SourceEntity> entities = this.databaseManager.getEntities();

		Log.info("There is " + entities.count() + " kos to export");

		for (SourceEntity entity : entities.list()) {

			InputStream targetStream = new ByteArrayInputStream(entity.rawKOS);
			try (DicomInputStream dis = new DicomInputStream(targetStream)) {
				dis.setIncludeBulkData(IncludeBulkData.URI);
				Attributes dataset = dis.readDataset();
				String instanceCreationDate = dataset.getString(Tag.InstanceCreationDate);

				String pathFolder = xdmPath + "/KA" + instanceCreationDate.substring(0, 6);
				String pathFolderZip = pathFolder;

				generateFolder(pathFolder);
				if(!checkFileExists(pathFolder + "/INDEX.HTM")) {
					generateIndex(pathFolder + "/INDEX.HTM", entity.rawMetadata);
				}
				if(!checkFileExists(pathFolder + "/README.TXT")) {
					generateREADME(pathFolder + "/README.TXT", entity.rawMetadata);
				}
				String pathFolderReadME = pathFolder;
				pathFolder += "/IHE_XDM";
				generateFolder(pathFolder);


				boolean checkExist = true;
				int i = 0;
				while(checkExist) {
					i++;
					checkExist = checkFolderExists(pathFolder + "/SS" + String.format("%06d", i));
					if(!checkExist) {
						pathFolder += "/SS" + String.format("%06d", i);
						generateFolder(pathFolder);
					}
				}

				DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
				factory.setNamespaceAware(true);
				DocumentBuilder builder = factory.newDocumentBuilder();

				String metadatas = new String(entity.rawMetadata, StandardCharsets.UTF_8);
				Document document = builder.parse(new ByteArrayInputStream(metadatas.split("\\?>")[1].getBytes()));
				NodeList nodeList = document.getFirstChild().getChildNodes().item(1).getFirstChild().getFirstChild().getFirstChild().getChildNodes();
				int z = 1;
				for (int j = 0; j < nodeList.getLength(); j++) {
					if(nodeList.item(j).getNodeName().equals("ExtrinsicObject")) {
						if(nodeList.item(j).getAttributes().getNamedItem("mimeType").getNodeValue().equals("application/dicom")) {

							var slotElement = document.createElement("Slot");
							slotElement.setAttribute("name", "URI");

							var valueList = document.createElement("ValueList");
							slotElement.appendChild(valueList);

							var valueField = document.createElement("Value");
							valueField.appendChild(document.createTextNode(pathFolder.substring(StringUtils.ordinalIndexOf(pathFolder, "/", 3) + 1, pathFolder.length()) + "/KOS_" + i + "_" + z + ".DCM"));
							valueList.appendChild(valueField);

							nodeList.item(j).appendChild(slotElement);
							
					        var externalIdentifierElement = document.createElement("ExternalIdentifier");
					        externalIdentifierElement.setAttribute("id", "80");
					        externalIdentifierElement.setAttribute("identificationScheme", "1.2.3");
					        externalIdentifierElement.setAttribute("registryObject", "DocumentKOS");
					        externalIdentifierElement.setAttribute("value", UUID.randomUUID().toString());
					        
					        var nameElement = document.createElement("Name");
					        var localizedString = document.createElement("LocalizedString");
					        localizedString.setAttribute("charset", "UTF8");
					        localizedString.setAttribute("xml:lang", "FR");
					        localizedString.setAttribute("value", "XDSDocumentEntry.EntryUUID");
					        nameElement.appendChild(localizedString);
					        
					        externalIdentifierElement.appendChild(nameElement);
							nodeList.item(j).appendChild(externalIdentifierElement);
							
						}
						else {
							var slotElement = document.createElement("Slot");
							slotElement.setAttribute("name", "URI");

							var valueList = document.createElement("ValueList");
							slotElement.appendChild(valueList);

							var valueField = document.createElement("Value");
							valueField.appendChild(document.createTextNode(pathFolder.substring(StringUtils.ordinalIndexOf(pathFolder, "/", 3) + 1, pathFolder.length()) + "/SIGN_" + i + ".XML"));
							valueList.appendChild(valueField);

							nodeList.item(j).appendChild(slotElement);
						}
					}
				}

				generateFile(pathFolder + "/METADATA.XML", XMLUtils.xmlToString(document).getBytes());
				generateFile(pathFolder + "/KOS_" + i + "_" + z + ".DCM", entity.rawKOS);
				generateFile(pathFolder + "/SIGN_" + i + ".XML", entity.signDOC);

				String ins = metadatas.split("urn:uuid:58a6f841-87b3-4a3e-92fd-a8ffeff98427")[1].split("value=")[1];;
				ins = ins.substring(1, StringUtils.ordinalIndexOf(ins, "&", 2));
				generateCR(pathFolder + "/CR.TXT", entity.cdaID, entity.ipp, ins);
				addInReadME(pathFolderReadME, pathFolder, i, z);
				String sourceFile = pathFolderZip;
				FileOutputStream fos = new FileOutputStream( pathFolderZip + ".zip");
				ZipOutputStream zipOut = new ZipOutputStream(fos);

				File fileToZip = new File(sourceFile);
				zipFile(fileToZip, fileToZip.getName(), zipOut);
				zipOut.close();
				fos.close();

			} catch (Exception e) {
				Log.error("Error in export xdm");
				Log.error(e.getMessage());
			}
		}		
	}

	@GET
	@Path("/xdmImport")
	public void importXDM() {

		File file = new File(xdmPath);
		String[] directories = file.list((dir, name) -> new File(dir, name).isDirectory());
		for (String directory : directories) {
			importDB(xdmPath + "/" + directory + "/IHE_XDM");
		}
	}

	private void importDB(String pathFolder) {
		File file = new File(pathFolder);
		String[] directories = file.list((dir, name) -> new File(dir, name).isDirectory());
		for (String directory : directories) {

			directory = pathFolder + "/" + directory + "/";
			File dir = new File(directory);

			FilenameFilter kosFileFilter = (d, s) -> {
				return s.toUpperCase().startsWith("KOS");
			};

			FilenameFilter signFileFilter = (d, s) -> {
				return s.toUpperCase().startsWith("SIGN");
			};


			String[] kosFiles = dir.list(kosFileFilter);

			String[] signFiles = dir.list(signFileFilter);

			File kosFile = new File(directory + kosFiles[0]);
			try {

				byte[] kos = Files.readAllBytes(kosFile.toPath());
				String studyInstanceUID = "";
				InputStream targetStream = new ByteArrayInputStream(kos);
				try (DicomInputStream dis = new DicomInputStream(targetStream)) {
					dis.setIncludeBulkData(IncludeBulkData.URI);
					Attributes dataset = dis.readDataset();
					studyInstanceUID = dataset.getString(Tag.StudyInstanceUID);
				}catch (Exception e) {
					Log.error("Error in kos parsing");
					Log.error(e.getMessage());
				}

				File metadataFile = new File(directory + "/METADATA.XML");
				byte[] metadatas = Files.readAllBytes(metadataFile.toPath());

				File SignFile = new File(directory + signFiles[0]);
				byte[] signDoc = Files.readAllBytes(SignFile.toPath());

				String cr = Files.readString(Paths.get(directory + "/CR.TXT"), StandardCharsets.UTF_8);

				String idCDA = cr.split(";")[0] + "/" + cr.split(";")[1];
				String ipp = cr.split(";")[4] + "/" + cr.split(";")[5];

				databaseManager.addEntity(kos, metadatas, signDoc, studyInstanceUID, idCDA, ipp);

				Log.info("import of study : " + studyInstanceUID);
			} catch (IOException e) {
				System.out.println("Error in Import DB.");
				e.printStackTrace();
			}
		}
	}

	private void generateIndex(String pathFolder, byte[] rawMetadata) {
		try {

			String metadatas = new String(rawMetadata, StandardCharsets.UTF_8);
			String valueText = metadatas.split("authorInstitution")[1].split("<Value>")[1];

			FileWriter myWriter = new FileWriter(pathFolder);
			myWriter.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n"
					+ "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\"><html>\r\n"
					+ "Emetteur : " + valueText.substring(0, valueText.indexOf('^'))
					+ " (" + valueText.substring(StringUtils.ordinalIndexOf(valueText, "^", 9) + 1, valueText.indexOf("<")) + ")\r\n"
					+ "Voir le fichier <a href=\"README.TXT\">ReadMe</a>\r\n"
					+ "</html>");
			myWriter.close();
			System.out.println("Successfully wrote to the file.");
		} catch (IOException e) {
			System.out.println("An error occurred in the index.");
			e.printStackTrace();
		}
	}

	private void generateREADME(String pathFolder, byte[] rawMetadata) {
		try {

			String metadatas = new String(rawMetadata, StandardCharsets.UTF_8);
			String valueText = metadatas.split("authorInstitution")[1].split("<Value>")[1];
			String authorName = metadatas.split("authorPerson")[1].split("<Value>")[1];
			FileWriter myWriter = new FileWriter(pathFolder);
			myWriter.write("Emetteur :\r\n"
					+ "=============\r\n"
					+ "    . Nom : DR " + authorName.substring(StringUtils.ordinalIndexOf(authorName, "^", 1) + 1, StringUtils.ordinalIndexOf(authorName, "^", 2)) + " "
					+ authorName.substring(StringUtils.ordinalIndexOf(authorName, "^", 2) + 1, StringUtils.ordinalIndexOf(authorName, "^", 3)) + "\r\n"
					+ "    . Organisme : " + valueText.substring(0, valueText.indexOf('^'))
					+ " (" + valueText.substring(StringUtils.ordinalIndexOf(valueText, "^", 9) + 1, valueText.indexOf("<")) + ")\r\n"
					+ "    . Adresse : 8 Rue Frédéric Bastia 92100 BOULOGNE-BILLANCOURT\r\n"
					+ "    . Téléphone: tel:0174589607\r\n"
					+ "\r\n"
					+ "Application de l'emetteur :\r\n"
					+ "=========================\r\n"
					+ "    . Nom : BCOM\r\n"
					+ "    . Version : 1.2\r\n"
					+ "    . Editeur : BCOM\r\n"
					+ "\r\n"
					+ "Instructions :\r\n"
					+ "=============\r\n"
					+ ". Consultez les fichiers reçus par messagerie securisee de sante dans votre logiciel de professionnel de sante.\r\n"
					+ "\r\n"
					+ "Arborescence :\r\n"
					+ "============\r\n"
					+ "     README.TXT\r\n"
					+ "     INDEX.HTM\r\n"
					+ "     IHE_XDM\r\n");
			myWriter.close();
			System.out.println("Successfully wrote to the file.");
		} catch (IOException e) {
			System.out.println("An error occurred in the readME.");
			e.printStackTrace();
		}
	}

	private void generateFolder(String folderName) {
		try {
			Files.createDirectories(Paths.get(folderName));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private boolean checkFolderExists(String folderName) {
		java.nio.file.Path path = Paths.get(folderName);
		return Files.exists(path);
	}

	private boolean checkFileExists(String fileName) {
		java.nio.file.Path path = Paths.get(fileName);
		return Files.exists(path);
	}

	private void generateFile(String folderName, byte[] rawBytes) {
		try {
			Files.write(Paths.get(folderName), rawBytes);
		} catch (IOException e) {
			e.printStackTrace();
			System.out.println("An error occurred while generating file : " + folderName + ".");
		}
	}


	private void generateCR(String pathFolder, String cdaID, String ipp, String ins) {
		try {
			FileWriter myWriter = new FileWriter(pathFolder);
			String insID = ins.substring(0, ins.indexOf('^', 1));
			String insExtension = ins.substring(ins.indexOf(";", 0) +1, ins.length());
			myWriter.write(cdaID.split("/")[0] + ";" + cdaID.split("/")[1] + ";" + insID + ";" + insExtension+ ";" + ipp.split("/")[0] + ";" + ipp.split("/")[1]);
			myWriter.close();
			System.out.println("Successfully wrote to the file.");
		} catch (IOException e) {
			System.out.println("An error occurred in the CR.");
			e.printStackTrace();
		}
	}

	private void addInReadME(String pathFolder, String newPathFolder, Integer i, Integer z) {
		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(pathFolder + "/README.TXT", true));
			writer.append("          " + newPathFolder.split("/")[3] + "\r\n"
					+ "               METADATA.XML\r\n"
					+ "               CR.TXT\r\n"
					+ "               SIGN_" + i + ".XML\r\n"
					+ "               KOS_" + i + z + ".DCM\r\n");
			writer.flush();
			writer.close();
		} catch (IOException e) {
			System.out.println("An error occurred in the readME, adding new file.");
			e.printStackTrace();
		}
	}


	private static void zipFile(File fileToZip, String fileName, ZipOutputStream zipOut) throws IOException {
		if (fileToZip.isHidden()) {
			return;
		}
		if (fileToZip.isDirectory()) {
			if (fileName.endsWith("/")) {
				zipOut.putNextEntry(new ZipEntry(fileName));
				zipOut.closeEntry();
			} else {
				zipOut.putNextEntry(new ZipEntry(fileName + "/"));
				zipOut.closeEntry();
			}
			File[] children = fileToZip.listFiles();
			for (File childFile : children) {
				zipFile(childFile, fileName + "/" + childFile.getName(), zipOut);
			}
			return;
		}
		FileInputStream fis = new FileInputStream(fileToZip);
		ZipEntry zipEntry = new ZipEntry(fileName);
		zipOut.putNextEntry(zipEntry);
		byte[] bytes = new byte[1024];
		int length;
		while ((length = fis.read(bytes)) >= 0) {
			zipOut.write(bytes, 0, length);
		}
		fis.close();
	}
}




