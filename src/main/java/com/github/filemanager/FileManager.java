/*
The MIT License

Copyright (c) 2015-2023 Valentyn Kolesnikov (https://github.com/javadev/file-manager)

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
 */
package com.github.filemanager;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.io.*;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;
import javax.swing.filechooser.FileSystemView;
import javax.swing.table.*;
import javax.swing.tree.*;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

/**
 * A basic File Manager. Requires 1.6+ for the Desktop &amp; SwingWorker classes, amongst other
 * minor things.
 *
 * <p>Includes support classes FileTableModel &amp; FileTreeCellRenderer.
 *
 * <p>TODO Bugs
 *
 * <ul>
 *   <li>Still throws occasional AIOOBEs and NPEs, so some update on the EDT must have been missed.
 *   <li>Fix keyboard focus issues - especially when functions like rename/delete etc. are called
 *       that update nodes &amp; file lists.
 *   <li>Needs more testing in general.
 *       <p>TODO Functionality
 *   <li>Implement Read/Write/Execute checkboxes
 *   <li>Implement Copy
 *   <li>Extra prompt for directory delete (camickr suggestion)
 *   <li>Add File/Directory fields to FileTableModel
 *   <li>Double clicking a directory in the table, should update the tree
 *   <li>Move progress bar?
 *   <li>Add other file display modes (besides table) in CardLayout?
 *   <li>Menus + other cruft?
 *   <li>Implement history/back
 *   <li>Allow multiple selection
 *   <li>Add file search
 * </ul>
 */
public class FileManager {

    /**
     * Title of the application
     */
    public static final String APP_TITLE = "FileMan";
    /**
     * Used to open/edit/print files.
     */
    private Desktop desktop;
    /**
     * Provides nice icons and names for files.
     */
    private FileSystemView fileSystemView;

    /**
     * currently selected File.
     */
    private File currentFile;

    /**
     * Main GUI container
     */
    private JPanel gui;

    /**
     * File-system tree. Built Lazily
     */
    private JTree tree;

    private DefaultTreeModel treeModel;

    /**
     * Directory listing
     */
    private JTable table;

    private JProgressBar progressBar;
    /**
     * Table model for File[].
     */
    private FileTableModel fileTableModel;

    private ListSelectionListener listSelectionListener;
    private boolean cellSizesSet = false;
    private int rowIconPadding = 6;

    /* File controls. */
    private JButton openFile;
    private JButton printFile;
    private JButton editFile;
    private JButton deleteFile;
    private JButton newdeltefile;
    private JButton newFile;
    private JButton copyFile;

    /* 새로 추가한 git 버튼 */
    private JButton gitInitFile; // git init
    private JButton gitAddFile; // git add
    private JButton gitCommitFile; // git commit
    private JButton gitMvFile; // git mv
    private JButton gitRmFile; // git rm
    private JButton gitRmCachedFile; // git rm --cached
    private JButton gitRestoreFile; // git restore
    private JButton gitRestoreStagedFile; // git restore --staged


    /* File details. */
    private JLabel fileName;
    private JTextField path;
    private JLabel date;
    private JLabel size;
    private JCheckBox readable;
    private JCheckBox writable;
    private JCheckBox executable;
    private JRadioButton isDirectory;
    private JRadioButton isFile;

    /* GUI options/containers for new File/Directory creation.  Created lazily. */
    private JPanel newFilePanel;
    private JRadioButton newTypeFile;
    private JTextField name;

    /* git commit을 눌렀을 때 나오는 새 창을 위한 GUI options/containers */
    private JPanel gitCommitPanel;
    private JTextField gitCommitMessage;

    /* List에서 File을 선택했는지 Tree에서 File을 선택헀는지 분간하기 위한 변수. True일 경우에만 git 버튼이 활성화된다.*/
    private boolean isFileSelectedInList = false;

    public Container getGui() {
        if (gui == null) {
            gui = new JPanel(new BorderLayout(3, 3));
            gui.setBorder(new EmptyBorder(5, 5, 5, 5));

            fileSystemView = FileSystemView.getFileSystemView();
            desktop = Desktop.getDesktop();

            JPanel detailView = new JPanel(new BorderLayout(3, 3));
            // fileTableModel = new FileTableModel();

            table = new JTable();
            table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            table.setAutoCreateRowSorter(true);
            table.setShowVerticalLines(false);
            try {
                renderGitFileStatus(); //초기 렌더링 설정. 나중에 딜레이 안생김
            }catch (GitAPIException | IOException e){
                e.printStackTrace();
            }



            listSelectionListener = new ListSelectionListener() {
                @Override
                public void valueChanged(ListSelectionEvent lse) {
                    int row = table.getSelectionModel().getLeadSelectionIndex();
                    setFileDetails(((FileTableModel) table.getModel()).getFile(row));
                    isFileSelectedInList = true; //리스트에서 파일을 선택했으므로 true
                    if (isFileInGitRepository()){
                        try{
                            renderGitFileStatus(); //일관성 유지를 위해 선택한 파일이 바뀔 때마다 렌더링한다. (성능은 감소할 수 있음)
                        }catch(IOException | GitAPIException e){
                            e.printStackTrace();
                        }
                    }
                }
            };
            table.getSelectionModel().addListSelectionListener(listSelectionListener);
            JScrollPane tableScroll = new JScrollPane(table);
            Dimension d = tableScroll.getPreferredSize();
            tableScroll.setPreferredSize(new Dimension((int) d.getWidth(), (int) d.getHeight() / 2));
            detailView.add(tableScroll, BorderLayout.CENTER);

            // the File tree
            DefaultMutableTreeNode root = new DefaultMutableTreeNode();
            treeModel = new DefaultTreeModel(root);

            TreeSelectionListener treeSelectionListener = new TreeSelectionListener() {
                public void valueChanged(TreeSelectionEvent tse) {
                    DefaultMutableTreeNode node = (DefaultMutableTreeNode) tse.getPath().getLastPathComponent();
                    showChildren(node);
                    setFileDetails((File) node.getUserObject());
                    isFileSelectedInList = false; //리스트가 아닌 트리에서 파일을 선택했으므로 false. 이때는 git 버튼이 비활성화된다.
                    if(isTreeInGitRepository()){
                        try{
                            renderGitFileStatus(); //디렉토리가 변경될 때마다 또 바뀐 파일 목록의 status를 출력을 해줘야 하므로 렌더링한다.
                        }catch(IOException | GitAPIException e){
                            e.printStackTrace();
                        }
                    }
                }
            };

            // show the file system roots.
            File[] roots = fileSystemView.getRoots();
            for (File fileSystemRoot : roots) {
                DefaultMutableTreeNode node = new DefaultMutableTreeNode(fileSystemRoot);
                root.add(node);
                // showChildren(node);
                //
                File[] files = fileSystemView.getFiles(fileSystemRoot, true);
                for (File file : files) {
                    if (file.isDirectory()) {
                        node.add(new DefaultMutableTreeNode(file));
                    }
                }
                //
            }


            tree = new JTree(treeModel);
            tree.setRootVisible(false);
            tree.addTreeSelectionListener(treeSelectionListener);
            tree.setCellRenderer(new FileTreeCellRenderer());
            tree.expandRow(0);
            JScrollPane treeScroll = new JScrollPane(tree);

            // as per trashgod tip
            tree.setVisibleRowCount(15);

            Dimension preferredSize = treeScroll.getPreferredSize();
            Dimension widePreferred = new Dimension(200, (int) preferredSize.getHeight());
            treeScroll.setPreferredSize(widePreferred);

            // details for a File
            JPanel fileMainDetails = new JPanel(new BorderLayout(4, 2));
            fileMainDetails.setBorder(new EmptyBorder(0, 6, 0, 6));

            JPanel fileDetailsLabels = new JPanel(new GridLayout(0, 1, 2, 2));
            fileMainDetails.add(fileDetailsLabels, BorderLayout.WEST);

            JPanel fileDetailsValues = new JPanel(new GridLayout(0, 1, 2, 2));
            fileMainDetails.add(fileDetailsValues, BorderLayout.CENTER);

            fileDetailsLabels.add(new JLabel("File", JLabel.TRAILING));
            fileName = new JLabel();
            fileDetailsValues.add(fileName);
            fileDetailsLabels.add(new JLabel("Path/name", JLabel.TRAILING));
            path = new JTextField(5);
            path.setEditable(false);
            fileDetailsValues.add(path);
            fileDetailsLabels.add(new JLabel("Last Modified", JLabel.TRAILING));
            date = new JLabel();
            fileDetailsValues.add(date);
            fileDetailsLabels.add(new JLabel("File size", JLabel.TRAILING));
            size = new JLabel();
            fileDetailsValues.add(size);
            fileDetailsLabels.add(new JLabel("Type", JLabel.TRAILING));

            JPanel flags = new JPanel(new FlowLayout(FlowLayout.LEADING, 4, 0));
            isDirectory = new JRadioButton("Directory");
            isDirectory.setEnabled(false);
            flags.add(isDirectory);

            isFile = new JRadioButton("File");
            isFile.setEnabled(false);
            flags.add(isFile);
            fileDetailsValues.add(flags);

            int count = fileDetailsLabels.getComponentCount();
            for (int ii = 0; ii < count; ii++) {
                fileDetailsLabels.getComponent(ii).setEnabled(false);
            }

            JToolBar toolBar = new JToolBar();
            // mnemonics stop working in a floated toolbar
            toolBar.setFloatable(false);

            openFile = new JButton("Open");
            openFile.setMnemonic('o');

            openFile.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ae) {
                    try {
                        desktop.open(currentFile);
                    } catch (Throwable t) {
                        showThrowable(t);
                    }
                    gui.repaint();
                }
            });
            toolBar.add(openFile);

            editFile = new JButton("Edit");
            editFile.setMnemonic('e');
            editFile.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ae) {
                    try {
                        desktop.edit(currentFile);
                    } catch (Throwable t) {
                        showThrowable(t);
                    }
                }
            });
            toolBar.add(editFile);

            printFile = new JButton("Print");
            printFile.setMnemonic('p');
            printFile.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ae) {
                    try {
                        desktop.print(currentFile);
                    } catch (Throwable t) {
                        showThrowable(t);
                    }
                }
            });
            // toolBar.add(printFile);

            // Check the actions are supported on this platform!
            openFile.setEnabled(desktop.isSupported(Desktop.Action.OPEN));
            editFile.setEnabled(desktop.isSupported(Desktop.Action.EDIT));
            // printFile.setEnabled(desktop.isSupported(Desktop.Action.PRINT));


            newFile = new JButton("New");
            newFile.setMnemonic('n');
            newFile.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ae) {
                    newFile();
                }
            });
            toolBar.add(newFile);

            copyFile = new JButton("Copy");
            copyFile.setMnemonic('c');
            copyFile.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ae) {
                    showErrorMessage("'Copy' not implemented.", "Not implemented.");
                }
            });
            // toolBar.add(copyFile);

            JButton renameFile = new JButton("Rename");
            renameFile.setMnemonic('r');
            renameFile.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ae) {
                    renameFile();
                }
            });
            // toolBar.add(renameFile);

            deleteFile = new JButton("Delete");
            deleteFile.setMnemonic('d');
            deleteFile.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ae) {
                    deleteFile();
                }
            });
            toolBar.add(deleteFile);

            toolBar.addSeparator();
            /* git 버튼 객체 생성 로직 */

            // 1. init 버튼
            gitInitFile = new JButton("Git init");
            gitInitFile.setMnemonic('I');
            gitInitFile.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ae) {
                    gitInitFile();
                }
            });
            toolBar.add(gitInitFile);

            // 2. add 버튼
            gitAddFile = new JButton("Git add");
            gitAddFile.setMnemonic('A');
            gitAddFile.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ae) {
                    gitAddFile();
                }
            });
            toolBar.add(gitAddFile);

            // 3. commit 버튼
            gitCommitFile = new JButton("Git commit");
            gitCommitFile.setMnemonic('C');
            gitCommitFile.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ae) {
                    gitCommitFile();
                }
            });
            toolBar.add(gitCommitFile);

            // 4. mv 버튼
            gitMvFile = new JButton("Git mv");
            gitMvFile.setMnemonic('M');
            gitMvFile.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ae) { gitMvFile(); }
            });
            toolBar.add(gitMvFile);

            // 5. rm 버튼
            gitRmFile = new JButton("Git rm");
            gitRmFile.setMnemonic('R');
            gitRmFile.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ae) {
                    gitRmFile();
                }
            });
            toolBar.add(gitRmFile);

            // 6. rm --cached 버튼
            gitRmCachedFile = new JButton("Git rm --cached");
            gitRmCachedFile.setMnemonic('R');
            gitRmCachedFile.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ae) {
                    // 여기에다가 rm --cached 로직 추가
                }
            });
            toolBar.add(gitRmCachedFile);

            // 7. restore 버튼
            gitRestoreFile = new JButton("Git restore");
            gitRestoreFile.setMnemonic('R');
            gitRestoreFile.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ae) {gitRestoreFile();}
            });
            toolBar.add(gitRestoreFile);

            // 8. restore --staged 버튼
            gitRestoreStagedFile = new JButton("Git restore --staged");
            gitRestoreStagedFile.setMnemonic('R');
            gitRestoreStagedFile.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ae) {gitRestoreStagedFile();}
            });
            toolBar.add(gitRestoreStagedFile);

            readable = new JCheckBox("Read  ");
            readable.setMnemonic('a');
            // readable.setEnabled(false);
            toolBar.add(readable);

            writable = new JCheckBox("Write  ");
            writable.setMnemonic('w');
            // writable.setEnabled(false);
            toolBar.add(writable);

            executable = new JCheckBox("Execute");
            executable.setMnemonic('x');
            // executable.setEnabled(false);
            toolBar.add(executable);

            JPanel fileView = new JPanel(new BorderLayout(3, 3));

            fileView.add(toolBar, BorderLayout.NORTH);
            fileView.add(fileMainDetails, BorderLayout.CENTER);

            detailView.add(fileView, BorderLayout.SOUTH);

            JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, treeScroll, detailView);
            gui.add(splitPane, BorderLayout.CENTER);

            JPanel simpleOutput = new JPanel(new BorderLayout(3, 3));
            progressBar = new JProgressBar();
            simpleOutput.add(progressBar, BorderLayout.EAST);
            progressBar.setVisible(false);

            gui.add(simpleOutput, BorderLayout.SOUTH);
        }
        return gui;
    }

    public void showRootFile() {
        // ensure the main files are displayed
        tree.setSelectionInterval(0, 0);
    }

    private TreePath findTreePath(File find) {
        for (int ii = 0; ii < tree.getRowCount(); ii++) {
            TreePath treePath = tree.getPathForRow(ii);
            Object object = treePath.getLastPathComponent();
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) object;
            File nodeFile = (File) node.getUserObject();

            if (nodeFile.equals(find)) {
                return treePath;
            }
        }
        // not found!
        return null;
    }

    private void renameFile() {
        if (currentFile == null) {
            showErrorMessage("No file selected to rename.", "Select File");
            return;
        }

        String renameTo = JOptionPane.showInputDialog(gui, "New Name");
        if (renameTo != null) {
            try {
                boolean directory = currentFile.isDirectory();
                TreePath parentPath = findTreePath(currentFile.getParentFile());
                DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode) parentPath.getLastPathComponent();

                boolean renamed = currentFile.renameTo(new File(currentFile.getParentFile(), renameTo));
                if (renamed) {
                    if (directory) {
                        // rename the node..

                        // delete the current node..
                        TreePath currentPath = findTreePath(currentFile);
                        System.out.println(currentPath);
                        DefaultMutableTreeNode currentNode = (DefaultMutableTreeNode) currentPath.getLastPathComponent();

                        treeModel.removeNodeFromParent(currentNode);

                        // add a new node..
                        try{
                            renderGitFileStatus(); //이름의 변경사항 역시 기록되므로 렌더링
                        }catch (IOException | GitAPIException e){
                            e.printStackTrace();
                        }
                    }

                    showChildren(parentNode);
                } else {
                    String msg = "The file '" + currentFile + "' could not be renamed.";
                    showErrorMessage(msg, "Rename Failed");
                }
            } catch (Throwable t) {
                showThrowable(t);
            }
        }
        gui.repaint();
    }

    private void deleteFile() {
        if (currentFile == null) {
            showErrorMessage("No file selected for deletion.", "Select File");
            return;
        }

        int result = JOptionPane.showConfirmDialog(gui, "Are you sure you want to delete this file?", "Delete File", JOptionPane.ERROR_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
            try {
                System.out.println("currentFile: " + currentFile);
                TreePath parentPath = findTreePath(currentFile.getParentFile());
                System.out.println("parentPath: " + parentPath);
                DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode) parentPath.getLastPathComponent();
                System.out.println("parentNode: " + parentNode);

                boolean directory = currentFile.isDirectory();
                if (FileUtils.deleteQuietly(currentFile)) {
                    if (directory) {
                        // delete the node..
                        TreePath currentPath = findTreePath(currentFile);
                        System.out.println(currentPath);
                        DefaultMutableTreeNode currentNode = (DefaultMutableTreeNode) currentPath.getLastPathComponent();

                        treeModel.removeNodeFromParent(currentNode);
                    }

                    showChildren(parentNode);
                } else {
                    String msg = "The file '" + currentFile + "' could not be deleted.";
                    showErrorMessage(msg, "Delete Failed");
                }
            } catch (Throwable t) {
                showThrowable(t);
            }
        }
        gui.repaint();
    }

    private void newFile() {
        if (currentFile == null) {
            showErrorMessage("No location selected for new file.", "Select Location");
            return;
        }

        if (newFilePanel == null) {
            newFilePanel = new JPanel(new BorderLayout(3, 3));

            JPanel southRadio = new JPanel(new GridLayout(1, 0, 2, 2));
            newTypeFile = new JRadioButton("File", true);
            JRadioButton newTypeDirectory = new JRadioButton("Directory");
            ButtonGroup bg = new ButtonGroup();
            bg.add(newTypeFile);
            bg.add(newTypeDirectory);
            southRadio.add(newTypeFile);
            southRadio.add(newTypeDirectory);

            name = new JTextField(15);

            newFilePanel.add(new JLabel("Name"), BorderLayout.WEST);
            newFilePanel.add(name);
            newFilePanel.add(southRadio, BorderLayout.SOUTH);
        }

        int result = JOptionPane.showConfirmDialog(gui, newFilePanel, "Create File", JOptionPane.OK_CANCEL_OPTION);
        if (result == JOptionPane.OK_OPTION) {
            try {
                boolean created;
                File parentFile = currentFile;
                if (!parentFile.isDirectory()) {
                    parentFile = parentFile.getParentFile();
                }
                File file = new File(parentFile, name.getText());
                if (newTypeFile.isSelected()) {
                    created = file.createNewFile();
                } else {
                    created = file.mkdir();
                }
                if (created) {

                    TreePath parentPath = findTreePath(parentFile);
                    DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode) parentPath.getLastPathComponent();

                    if (file.isDirectory()) {
                        // add the new node..
                        DefaultMutableTreeNode newNode = new DefaultMutableTreeNode(file);

                        TreePath currentPath = findTreePath(currentFile);
                        DefaultMutableTreeNode currentNode = (DefaultMutableTreeNode) currentPath.getLastPathComponent();

                        treeModel.insertNodeInto(newNode, parentNode, parentNode.getChildCount());
                        try{
                            renderGitFileStatus();
                        }catch (IOException | GitAPIException e){
                            e.printStackTrace();
                        }
                    }

                    showChildren(parentNode);
                } else {
                    String msg = "The file '" + file + "' could not be created.";
                    showErrorMessage(msg, "Create Failed");
                }
            } catch (Throwable t) {
                showThrowable(t);
            }
        }
        gui.repaint();
    }

//
//     5/3 Bug Report
//     밑의 isInGitRepository() 함수를 사용하는 경우 단일 파일 선택 후 버튼 클릭 시 (git init, git add 등), 오류가 발생하는 것 같아 대체가 필요해보입니다.
//
//     5/5 수정
//     파일별 git 상태를 출력할 때 밑의 함수가 필요합니다. 트리 선택창에서 아래의 함수를 실행해야 .git이 있는지 확인가능합니다.
//     따라서 isTreeInGitRepository()로 함수 이름을 변경합니다.
//

    /**
     * 왼쪽 Tree 테이블에서 선택한 디렉토리가 .git에 포함되어있는지 확인하는 함수
     * */

    private boolean isTreeInGitRepository() { // 현재 directory가 git repository인지 판정하는 함수, 이 조건을 충족한 뒤 git 명령어를 실행해야 한다.
        String[] gitCheckCommand = {"git", "status"}; //Git status 명령어를 통해 간접적으로 .git 폴더 유무 확인
        ProcessBuilder processBuilder = new ProcessBuilder(gitCheckCommand);
        processBuilder.directory(currentFile); //선택한 파일의 경로 반환
        Process process;
        int gitStatus = -1;

        try{
            process = processBuilder.start(); //git status 명령어를 선택한 파일의 경로에서 실행하여 git이 있는지 확인
            gitStatus = process.waitFor(); //만일 git status가 잘 실행됐다면 git repository에 있다는 것이고, 아니라면 에러코드를 반환
            if (gitStatus == 0){
                return true;
            }else{
                return false;
            }
        }catch (IOException | InterruptedException e){
            e.printStackTrace();
        }
        return false;
    }


    private void gitInitFile() { // git init 명령어을 실행하는 함수
        if (currentFile == null) {
            showErrorMessage("No location selected for new file.", "Select Location");
            return;
        }

        if((isFileSelectedInList && isFileInGitRepository()) || (!isFileSelectedInList && isTreeInGitRepository())) {
            showErrorMessage("이 파일은 이미 .git이 존재합니다.", "Already Initialized");
            return;
        }
        try {
            int result = JOptionPane.showConfirmDialog(gui, "이 directory를 Git Repository로 등록하시겠습니까? '예'를 누르면 등록됩니다.", "git init", JOptionPane.ERROR_MESSAGE);
            // git init을 진짜 실행할건지 묻는 메시지 창
            if (result == JOptionPane.OK_OPTION) { // "예" 클릭 시 git init 명령어 실행
                String[] command = {"git", "init"};
                ProcessBuilder processBuilder = new ProcessBuilder(command);
                if (currentFile.isFile()){ //단일 파일을 경로로 선택하고 git init실행시 오류 발생하므로, 부모 경로로 설정한 후 git init 실행
                    processBuilder.directory(currentFile.getParentFile());
                }else{ //디렉토리를 경로로 선택하고 git init 실행 시 현재 경로 그대로 가져오기
                    processBuilder.directory(currentFile);
                }
                Process process = processBuilder.start();
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                }
                int exitCode = process.waitFor();
                if (exitCode == 0) { // git init 명령어가 정상적으로 실행되어 status가 0일 경우
                    JOptionPane.showMessageDialog(gui, "성공적으로 git init을 완료했습니다.");
                    System.out.println("git init");
                    try {
                        if (currentFile.isFile()){ //폴더를 git init했을 경우, 해당 폴더 내에서 .git이 생성되므로 렌더링 X
                            renderGitFileStatus(); //git init을 했을 경우, 파일에 변화가 일어났으므로 렌더링
                        }
                    } catch (IOException | GitAPIException e) {
                        e.printStackTrace();
                    }
                } else { //git init 명령어가 정상적으로 실행되지 않았을 경우
                    showErrorMessage("git init을 하는 과정에서 오류가 발생했습니다.", "git init error");
                }
            }
        }catch (IOException | InterruptedException e){
            e.printStackTrace();
        }

    }



    //5/3 Bug Report
//     * 밑의 isFileInGitRepository() 함수로 대체하면 예외처리 발생을 막는 것 같습니다.
//     * 후의 기능 git rm, git rm --cached 등의 기능에서도 아래의 함수를 사용하면 될 듯 싶음.
//      *
//      * 5/5
//      * 파일색상을 변경할 때, 트리에서 선택했을 경우 위의 함수가 필요하므로 사용해야 합니다.
//     */

    /**
     * 단일 파일을 선택했을 때, 해당 파일이 .git에 등록되어있는지 판별하는 boolean 함수.
     */

    private boolean isFileInGitRepository(){
        String[] gitCheckCommand = {"git", "status"}; //Git status 명령어를 통해 간접적으로 .git 폴더 유무 확인
        ProcessBuilder processBuilder = new ProcessBuilder(gitCheckCommand);
        processBuilder.directory(currentFile.getParentFile()); //선택한 파일의 경로 반환
        Process process;
        int gitStatus = -1;

        try{
            process = processBuilder.start(); //git status 명령어를 선택한 파일의 경로에서 실행하여 git이 있는지 확인
            gitStatus = process.waitFor(); //만일 git status가 잘 실행됐다면 git repository에 있다는 것이고, 아니라면 에러코드를 반환
            if (gitStatus == 0){
                return true;
            }else{
                return false;
            }
        }catch (IOException | InterruptedException e){
            e.printStackTrace();
        }
        return false;
    }

    private void gitAddFile() { //선택한 파일을 stage하는 git add로직. "git add" 버튼을 누르면 이 로직이 실행된다.
        if (currentFile == null || !isFileSelectedInList) {
            showErrorMessage("파일을 선택해주세요.", "Select File");
            return;
        }

        // ----------------------------------------확인 바람------------------------------------------
//          파일을 선택할 경우, directory가 아니므로 기존 isInGitRepository() 실행 시 Not a directory Error 발생.
//          따라서, git status를 통해 선택한 파일이 git repository에 있는지 확인 후 add 실행.
//          git status는 parent directory까지 자동으로 탐색하므로 번거로운 일이 줄어들 것으로 예상.
//          후에 필요할 시, 별도의 boolean 함수로 전환할 예정. => isFileInGitRepository로 함수화 완료. (5/2)
//          파일을 직접 선택할 경우, processBuilder.directory()의 인자에는 Directory가 들어가야 하므로
//          currentFile.getparentFile() 을 통해, 파일이 존재하는 경로를 반환해야 함.
//

        if (isFileInGitRepository()) { //현재 디렉토리에 .git이 있는 경우에만 add 실행가능하게 함.
            try{
                int result = JOptionPane.showConfirmDialog(gui, "해당 파일을 stage 하시겠습니까? '예'를 누르면 등록됩니다.", "git add", JOptionPane.ERROR_MESSAGE);

                if (result == JOptionPane.OK_OPTION) { // "예" 클릭 시 git add 명령어 실행
                    String[] gitAddCommand = {"git", "add", currentFile.getAbsolutePath()};
                    ProcessBuilder processBuilder = new ProcessBuilder(gitAddCommand);
                    processBuilder.directory(currentFile.getParentFile());
                    Process process = processBuilder.start();
                    int addStatus = process.waitFor(); //git add 명령어 정상 실행 여부

                    if (addStatus == 0){ // git add 명령어가 정상적으로 실행되어 status가 0일 경우
                        JOptionPane.showMessageDialog(gui, "성공적으로 파일을 stage 했습니다.");
                        System.out.println(currentFile);
                        System.out.println("staged");
                        try{
                            renderGitFileStatus(); //스테이지했을 경우, 파일에 변화가 일어났으므로 렌더링
                        }catch (IOException | GitAPIException e){
                            e.printStackTrace();
                        }
                    }else{ //git add 명령어가 정상적으로 실행되지 않았을 경우
                        showErrorMessage("파일을 Stage하는 과정에서 오류가 발생했습니다.","git add error");
                    }
                }
            } catch (InterruptedException | IOException e){
                e.printStackTrace();
            }
        }else{ //.git이 존재하지 않는 경우 (git status 명령어가 실패했을 경우)
            showErrorMessage("선택한 파일은 git repository에 존재하지 않습니다.","git Add error");
        }
    }

    /**
     * 커밋 창을 출력할 때, staged된 파일의 목록을 가져오기 위한 함수.
     * @throws
     * IOException
     * GitAPIException
     */

    private Object[][] getStagedFile(File curFile) throws IOException, GitAPIException {
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        File gitDir = builder.findGitDir(curFile).getGitDir(); // .git 폴더 찾기
        Repository repository = builder.setGitDir(gitDir).readEnvironment().findGitDir().build(); // Repository 객체 생성
        Git git = new Git(repository);

        // Stage된 파일 목록 가져오기
        Status status = git.status().call();
        Set<String>staged = status.getAdded();
        Set<String> changed = status.getChanged(); //변경사항이 stage되면 changed로 상태가 바뀌므로 따로 가져옴
        Set<String> removed = status.getRemoved();

        // 테이블 데이터로 변환하기
        Object[][] data = new Object[staged.size() + changed.size() + removed.size()][2];
        int i = 0;
        for (String fileName : staged) {
            String fileStatus = "new file";
            data[i] = new Object[]{fileName, fileStatus};
            i++;
        }
        for (String fileName : changed) {
            String fileStatus = "modified";
            data[i] = new Object[]{fileName, fileStatus};
            i++;
        }
        for (String fileName : removed) {
            String fileStatus = "deleted";
            data[i] = new Object[]{fileName, fileStatus};
            i++;
        }
        return data;
    }

    /**
     git Commit버튼을 누를 때 실행되는 commit 로직함수
     */
    private void gitCommitFile() { //git commit 로직
        JTextField textField;
        JTable commitTable;
        JLabel commitMessageLabel;
        JLabel stagedFileLabel;
        JScrollPane commitScrollPane;

        if (currentFile == null) { //선택한 파일이 없으면 에러 메시지. List가 아닌 Tree에서 파일을 선택했을 경우도 포함
            showErrorMessage("파일을 선택해주세요.", "Select File");
            return;
        }

        if ((isFileSelectedInList && isFileInGitRepository()) | (!isFileSelectedInList && isTreeInGitRepository())){ //.git이 존재할 경우
            if (gitCommitPanel == null) {
                try{
                    /*-------------------------------커밋 창 UI 구성--------------------------------- */
                    String[] columns = {"File", "File Status"};
                    Object[][] data = getStagedFile(currentFile); //stage된 데이터 오브젝트
                    JPanel commitTablePanel = new JPanel(new BorderLayout());
                    commitTable = new JTable(data, columns);
                    stagedFileLabel = new JLabel("Changes to be committed:");


                    // JTextField 생성
                    JPanel commitMessagePanel = new JPanel(new BorderLayout());
                    commitMessageLabel = new JLabel("Commit Message:");
                    textField = new JTextField(2);
                    commitMessagePanel.add(commitMessageLabel,BorderLayout.NORTH);
                    commitMessagePanel.add(textField, BorderLayout.SOUTH);


                    commitScrollPane = new JScrollPane(commitTable);
                    commitScrollPane.setPreferredSize(new Dimension(700,200));

                    commitTablePanel.add(commitScrollPane, BorderLayout.SOUTH);
                    commitTablePanel.add(stagedFileLabel, BorderLayout.NORTH);

                    // 커밋 창 구성
                    JPanel panel = new JPanel(new BorderLayout());
                    panel.add(commitTablePanel, BorderLayout.NORTH);
                    panel.add(commitMessagePanel, BorderLayout.SOUTH);

                   // 커밋 창 확인 취소 버튼을 "커밋", "취소" 버튼으로 커스터 마이징
                    Object[] choices = {"커밋", "취소"};
                    Object defaultChoice = choices[0];
                /*---------------------------------------------------------------------------*/

                    if (data.length == 0){ //Stage된 파일이 없을 경우 커밋 창 띄우지 말아야 함
                        showErrorMessage("Stage된 파일이 없습니다.", "No Staged File");
                        return;
                    }
                    //커밋 창 띄우기
                    int optionPane = JOptionPane.showOptionDialog(gui, panel, "Git Commit", JOptionPane.OK_CANCEL_OPTION,JOptionPane.QUESTION_MESSAGE,null,choices,defaultChoice);

                    //git commit -m 명령어 실행
                    if (optionPane == JOptionPane.OK_OPTION){ //커밋 버튼을 눌렀을 경우
                        if (textField.getText().isEmpty()){ //커밋 메시지를 입력하지 않았을 경우 에러 메시지 출력
                            showErrorMessage("커밋 메시지를 입력해주세요.","Empty commit message");
                        }else{
                            String[] gitCommitCommand = {"git", "commit","-m", textField.getText()};
                            ProcessBuilder processBuilder = new ProcessBuilder(gitCommitCommand);
                            processBuilder.directory(currentFile.getParentFile());
                            Process process = processBuilder.start();
                            int commitStatus = process.waitFor(); //git commit 명령어 정상 실행 여부

                            if (commitStatus == 0){ // git commit 명령어가 정상적으로 실행되어 status가 0일 경우
                                JOptionPane.showMessageDialog(gui, "성공적으로 파일을 커밋 했습니다.");
                                System.out.println(currentFile);
                                System.out.println("Committed");
                                try{
                                    renderGitFileStatus(); //커밋했을 경우, 파일에 변화가 일어났으므로 렌더링
                                }catch (IOException | GitAPIException e){
                                    e.printStackTrace();
                                }
                            }else{ //git commit 명령어가 정상적으로 실행되지 않았을 경우
                                showErrorMessage("파일을 Commit하는 과정에서 오류가 발생했습니다.","git commit error");
                            }
                        }
                    }
                }catch (GitAPIException | InterruptedException | IOException e){
                    e.printStackTrace();
                }
            }
        }else{ //.git이 존재하지 않을 경우
            showErrorMessage("선택한 파일은 git repository에 존재하지 않습니다.","git Commit error");
        }
    }

    /**
     git mv버튼을 누를 때 실행되는 mv 로직함수
     */
    private void gitMvFile(){
    /*
    파일을 클릭하고 버튼을 누르면 새 파일명을 입력받는 팝업창이 뜨고, 파일명을 입력, 확인 버튼을 누르면 새 파일명으로 변경된다.
    기존 파일명과 새 파일명이 같거나 파일명이 비어있다면 경고메세지(ex: 파일명이 동일합니다.)팝업창 출력.

    --> 1.버튼이 눌렸을 떄 선택된 파일이 있는지 확인 (o)
    --> 2.git repo안의 파일인지 확인 (o)
    --> 3.새 파일명을 입력받는 팝업창 생성 (o)
    --> 4.확인버튼 누르면 비어있는지 확인, 기존 파일들과 이름 비교 (o)
    --> 5.해당사항 없으면 새 파일명으로 변경(git rm file_from file_new 명령어 실행). (o)
    --> 6.결과에 따른 반환값에 따른 팝업창 생성 (o)
    */
        JFrame mvFrame;
        JPanel mvPanel, buttonPanel;
        JTextField textField;
        JLabel file_fromLabel;
        JButton okButton, cancelButton;
        //
        if (currentFile == null || !isFileSelectedInList) {//1.파일 선택하지 않았을 경우, 별도행위없이 함수종료
            showErrorMessage("파일을 선택해주세요.", "Select File");
            return;
        }

        if(isFileInGitRepository()) {//2.git repo안에 있는 경우에만 실행.
            if(isCommittedOrUnmodifiedFile(currentFile)) {//파일이 Committed, Unmodified 상태인 경우에만 실행
                //file_from과 file_to를 명시할 mvPanel
                mvPanel = new JPanel(new GridLayout(1, 4));
                JLabel file_from = new JLabel(" file_from:");
                JLabel file_to = new JLabel(" file_to:");
                file_from.setPreferredSize(new Dimension(50, 20));
                file_to.setPreferredSize(new Dimension(50, 20));

                //선택한 파일과 사용자 입력을 받을 JtextField
                file_fromLabel = new JLabel(" " + currentFile.getName());
                file_fromLabel.setPreferredSize(new Dimension(100, 20));
                textField = new JTextField();
                textField.setPreferredSize(new Dimension(100, 20));
                mvPanel.add(file_from);
                mvPanel.add(file_fromLabel);
                mvPanel.add(file_to);
                mvPanel.add(textField);
                mvPanel.setPreferredSize(new Dimension(300, 20));

                //동작을 구현할 buttonPanel
                okButton = new JButton("Ok");
                cancelButton = new JButton("Cancel");
                buttonPanel = new JPanel(new GridLayout());
                buttonPanel.add(cancelButton);
                buttonPanel.add(okButton);

                //Panel들을 포함할 Frame
                mvFrame = new JFrame();
                mvFrame.setLayout(new BorderLayout());
                mvFrame.add(mvPanel, BorderLayout.CENTER);
                mvFrame.add(buttonPanel, BorderLayout.SOUTH);
                mvFrame.setVisible(true);
                mvFrame.pack();
                mvFrame.setLocationRelativeTo(null);

                //okButton동작. 공란과 이미 존재하는 파일명에 대한 검사, 이상없을 시 git mv명령어 실행
                okButton.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        String file_from_string = currentFile.getName();
                        String file_to_string = textField.getText();
                        if (file_to_string.isEmpty()) {//파일명 공란검사
                            showErrorMessage("파일명은 공백이 될 수 없습니다.", "git mv error");
                        }
                        if (ifSameNameExist(file_to_string)) {//이미 존재하는 파일명 검사
                            showErrorMessage("이미 존재하는 파일명입니다.", "git mv error");
                        }
                        try {//git mv 명령어 실행
                            mvFrame.dispose();
                            String[] gitMvCommand = {"git", "mv", currentFile.getName(), file_to_string};
                            ProcessBuilder processBuilder = new ProcessBuilder(gitMvCommand);
                            processBuilder.directory(currentFile.getParentFile());
                            Process process = processBuilder.start();
                            File temp_parentFile = currentFile.getParentFile();
                            int mvStatus = process.waitFor(); //git add 명령어 정상 실행 여부
                            if (mvStatus == 0) { // git mv 명령어가 정상적으로 실행되어 status가 0일 경우
                                JOptionPane.showMessageDialog(gui, "성공적으로 파일을 Rename했습니다.");
                                System.out.println(file_from_string + " -> " + file_to_string);
                                System.out.println("Renamed");
                                currentFile = findRenamedFile(temp_parentFile, file_to_string);
                                try {
                                    renderGitFileStatus(); //스테이지 했을 경우, 파일에 변화가 일어났으므로 렌더링
                                    TreePath parentPath = findTreePath(currentFile.getParentFile());
                                    DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode) parentPath.getLastPathComponent();
                                    showChildren(parentNode);
                                } catch (IOException | GitAPIException e1) {
                                    e1.printStackTrace();
                                }
                                gui.repaint();

                            } else { //git mv 명령어가 정상적으로 실행되지 않았을 경우
                                showErrorMessage("파일을 Rename하는 과정에서 오류가 발생했습니다.", "git mv error");
                            }
                        } catch (InterruptedException | IOException e2) {
                            e2.printStackTrace();
                        }
                    }
                });

                cancelButton.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        mvFrame.dispose();
                    }
                });
            }
        }else{ //2. .git이 존재하지 않는 경우 (git status 명령어가 실패했을 경우)
            showErrorMessage("(현재 폴더 또는 상위 폴더 중 일부가) 깃 저장소가 아닙니다.","git mv error");
        }
        //
    }
    /**
     이름이 바뀌면 currentFile을 찾지 못하는 경우 발생, 미리 ParentFile을 가지고 있다가 이름이 바뀐 파일을 찾아내어서 currentFile을 최신화해주는 함수.
     */
    private File findRenamedFile(File parentFile, String fileName){
        File[] files = parentFile.listFiles();
        for(File file : files){
            if(file.getName().equals(fileName)){return file;}
        }
        return null;
    }
    /**
     해당 파일이 위차한 폴더 내에 같은 인자로 주어진 String 명이 이미 존재하는지 검사하는 함수.
     */
    private boolean ifSameNameExist(String fileName) { //현재 directory에 같은 이름의 파일이 이미 존재하는지 검사하는 함수
        File[] files = currentFile.getParentFile().listFiles();
        for (File file : files) {
            if (file.getName().equals(fileName)) { //부모 폴더에 입력한 이름과 같은 명의 파일이 존재한다면 true반환
                return true;
            }
        }
        return false;
    }

    private void gitRmFile(){
    /*
    파일을 클릭하고 버튼을 누르면 git rm 명령어 실행.

    --> 1.버튼이 눌렸을 떄 선택된 파일이 있는지 확인 (o)
    --> 2.git repo안의 파일인지 확인 (o)
    --> 3.해당 파일이 Committed 상태이거나 Unmodified상태인지 확인 (o).
    --> 4.git rm 명령어 실행 (o)
    --> 5.결과에 따른 반환값에 따른 팝업창 생성 (o)
    */

        if (currentFile == null) {//1.파일 선택하지 않았을 경우, 별도행위없이 함수종료
            showErrorMessage("파일을 선택해주세요.", "Select File");
            return;
        }

        if(isFileInGitRepository()) {//2.git repo안에 있는 경우에만 실행.
            try{
                if(isCommittedOrUnmodifiedFile(currentFile)) {//파일이 Committed, Unmodified 상태인 경우에만 실행
                    int result = JOptionPane.showConfirmDialog(gui, "해당 파일을 삭제하고 이 변화를 staged하시겠습니까?", "git rm", JOptionPane.ERROR_MESSAGE);

                    if (result == JOptionPane.OK_OPTION) { // "예" 클릭 시 git rm 명령어 실행
                        String[] gitRmCommand = {"git", "rm", currentFile.getName()};
                        ProcessBuilder processBuilder = new ProcessBuilder(gitRmCommand);
                        processBuilder.directory(currentFile.getParentFile());
                        Process process = processBuilder.start();
                        int rmStatus = process.waitFor(); //git rm 명령어 정상 실행 여부

                        if (rmStatus == 0) { // git rm 명령어가 정상적으로 실행되어 status가 0일 경우
                            JOptionPane.showMessageDialog(gui, "성공적으로 파일을 remove 했습니다.");
                            System.out.println(currentFile);
                            System.out.println("removed && change staged");
                            // renderGitFileStatus(); //파일이 단순히 삭제되는 것이므로 다른 파일 목록엔 변화가 없음. Exception 방지
                            isFileSelectedInList = false; //파일이 삭제되어 선택된 파일이 없으므로 false
                            TreePath parentPath = findTreePath(currentFile.getParentFile());
                            DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode) parentPath.getLastPathComponent();
                            showChildren(parentNode);
                            currentFile = currentFile.getParentFile();
                        } else { //git rm 명령어가 정상적으로 실행되지 않았을 경우
                            showErrorMessage("파일을 remove하는 과정에서 오류가 발생했습니다.", "git rm error");
                        }
                    }
                    gui.repaint();
                }
            } catch (InterruptedException | IOException e){
                e.printStackTrace();
            }
        }else{ //2. .git이 존재하지 않는 경우 (git status 명령어가 실패했을 경우)
            showErrorMessage("(현재 폴더 또는 상위 폴더 중 일부가) 깃 저장소가 아닙니다.","git rm error");
        }
        //
    }

    /**
     * 파일 목록에 각 파일들의 status에 따라 테이블의 색상을 다르게 설정하는 렌더링 함수. 디렉토리에서 폴더를 클릭하거나, 파일을 선택하거나,
     * 탐색기의 버튼을 누를 때마다 호출됨.
     * @throws IOException
     * @throws GitAPIException
     * @throws NullPointerException
     */

    private void renderGitFileStatus() throws IOException, GitAPIException, NullPointerException { //텍스트 색깔 렌더링 함수

        try {
            FileRepositoryBuilder builder = new FileRepositoryBuilder();
            File gitDir = builder.findGitDir(currentFile).getGitDir(); // .git 폴더 찾기
            Repository repository = builder.setGitDir(gitDir).readEnvironment().findGitDir().build(); // Repository 객체 생성
            Git git = new Git(repository);

            File workDir = repository.getWorkTree();
            Path workDirPath = Paths.get(workDir.getAbsolutePath());


            Status status = git.status().call(); //파일의 상태를 가져온다.

            Set<String> changedFiles = status.getChanged(); //변경사항이 staged 됐을 경우 -> Staged 색상과 동일
            Set<String> addedFiles = status.getAdded(); //새로운 파일이 added (staged) 됐을 경우 -> 초록색
            Set<String> modifiedFiles = status.getModified(); //Tracked 파일에 변경사항이 생겼을 경우 -> 주황색
            Set<String> untrackedFiles = status.getUntracked(); //새로운 파일이 생성되거나, untracked 파일일 경우 -> 빨간색



            table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() { //테이블 Render를 Override하여 색상을 변경할 수 있게 한다.
                public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                    Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                    FileTableModel model = (FileTableModel) table.getModel(); //테이블의 모델 가져오기
                    File file = model.getFile(row); //각 테이블의 행에 해당하는 파일을 가져온다.
                    Path relativePath = workDirPath.relativize(Paths.get(file.getAbsolutePath()));

                    if (addedFiles.contains(relativePath.toString())) { //그 파일이 added된 상태일 경우
                        c.setForeground(new Color(0,153,76)); //초록색
                    }
                    else if(modifiedFiles.contains(relativePath.toString())){ //그 파일이 변경되었을 경우
                        c.setForeground(new Color(255,128,0)); //주황색
                    }
                    else if (changedFiles.contains(relativePath.toString())) { //그 파일의 변경사항이 stage 되었을 경우
                        c.setForeground(new Color(0,153,76)); //초록색
                    } else if (untrackedFiles.contains(relativePath.toString())) { //그 파일이 untracked 상태이거나, 새로운 파일일 경우
                        c.setForeground(Color.RED); //빨간색
                    } else {
                        c.setForeground(table.getForeground()); //그 외의 경우 (commit된 상태) 기본 색상으로 설정
                    }
                    return c;
                }
            });
        } catch (IOException | GitAPIException | NullPointerException e) {
            e.printStackTrace();
        }
    }
    /**
     * 단일 파일을 선택 했을 때 해당 파일이 Commited or UnModified 상태인지 확인해 주는 boolean 함수
     */
    private boolean isCommittedOrUnmodifiedFile(File file){
        try{
            Git git;
            git = Git.open(currentFile.getParentFile());
            Status status = git.status().call();

            Set<String> untracked = status.getUntracked();  //Untracked 파일 이름을 받아와 비교
            Set<String> modified = status.getModified();    //Modified 파일 이름을 받아와 비교
            Set<String> added = status.getAdded();  //staged는 added와 changed가 있음. 두 가지 상태의 파일 이름을 받아와 비교
            Set<String> changed = status.getChanged();

            //untracked, modified, staged가 아니라면 Committed or Unmodified상태.
            if (!untracked.contains(file.getName()) && !modified.contains(file.getName())&& !added.contains(file.getName()) && !changed.contains(file.getName())){return true;}
            else{
                showErrorMessage("선택한 파일은 Committed나 UnModified 상태가 아닙니다. ", "Committed or Unmodified file chosen error");
            }
        }catch (IOException | GitAPIException e ){
            e.printStackTrace();
        }
        return false;
    }

    /**
     * 단일 파일을 선택 했을 때 해당 파일이 Modified 상태인지 확인해 주는 boolean 함수
    */
    private boolean isModifiedFile(File file) {
        try{
            Git git;
            git = Git.open(currentFile.getParentFile());
            Status status = git.status().call();

            Set<String> modified = status.getModified(); // Modified 파일 이름을 받아와 비교

            if (modified.contains(file.getName())){return true;} //파일 이름에 현재 파일이 있는 경우
            else{
                showErrorMessage("선택한 파일은 Modified 상태가 아닙니다. ", "UnModified file chosen error");
            }
        }catch (IOException | GitAPIException e ){
            e.printStackTrace();
        }
        return false;
    }

    /**
     * 단일 파일을 선택 했을 때 해당 파일이 Staged영역에 있는지 확인해 주는 boolean 함수
     */
    private boolean isStagedFile(File file){
        try{
            Git git;
            git = Git.open(currentFile.getParentFile());
            Status status = git.status().call();
            //staged 영역에 있는 경우는 2가지 존재
            Set<String> added = status.getAdded(); // 1. add 되고 수정이 없는 상태
            Set<String> changed = status.getChanged(); // 2. add 되고 수정이 있는 상태

            if (added.contains(file.getName()) || changed.contains(file.getName())){return true;} // stage 영역에 파일이 있으면 true 반환
            else{
                showErrorMessage("선택한 파일이 Stage 영역에 없습니다. ", "UnStaged file chosen error");
            }
        }catch (IOException | GitAPIException e ){
            e.printStackTrace();
        }
        return false;
    }

    /**
     * git restore 실행로직
    */
    private void gitRestoreFile(){ //git restore 실행 로직
        if (currentFile == null || !isFileSelectedInList) { //선택한 파일이 없으면 에러 메시지. List가 아닌 Tree에서 파일을 선택했을 경우도 포함
            showErrorMessage("파일을 선택해주세요.", "Select File");
            return;
        }
        if(isFileInGitRepository()){ //.git 파일이 있는 경우 진행
            try{
                if(isModifiedFile(currentFile)){ //선택한 파일이 Modified 상태인 경우
                    int result = JOptionPane.showConfirmDialog(gui, "해당 파일 혹은 디렉토리를 restore 하시겠습니까?", "git restore", JOptionPane.ERROR_MESSAGE);
                    if(result == JOptionPane.OK_OPTION){ //restore 여부에서 확인을 받은 경우 git restore 명령어 수행

                        String[] gitRestoreCommand = {"git", "restore", currentFile.getName()};
                        ProcessBuilder processBuilder = new ProcessBuilder(gitRestoreCommand);
                        processBuilder.directory(currentFile.getParentFile());
                        Process process = processBuilder.start();

                        int commitStatus = process.waitFor(); //git restore 명령어 정상 수행여부
                        if(commitStatus ==0){ // 정상수행을 의미
                            JOptionPane.showMessageDialog(gui, "복원이 성공적으로 이뤄졌습니다.");
                            System.out.println(currentFile);
                            System.out.println("Restored");
                            try{
                                renderGitFileStatus(); //restore 했을 경우, 파일에 변화가 일어났으므로 렌더링
                            }catch (IOException | GitAPIException e){
                                e.printStackTrace();
                            }
                        }else{ // 정상 수행이 아닌경우
                            showErrorMessage("복원 과정이 정상적으로 이뤄지지 않았습니다.", "Git restore error");
                        }
                    }else{return;} //사용자가 복원을 원치 않는 경우
                }
            }catch (IOException | InterruptedException e ){
                e.printStackTrace();
            }
        }else{ //.git이 존재하지 않는 경우
            showErrorMessage("선택한 파일은 .git repository에 존재하지 않습니다.", "Git restore error");
        }

    }
    /**
     * git restore --staged 실행로직
     */
    private void gitRestoreStagedFile(){
        if (currentFile == null || !isFileSelectedInList) { //선택한 파일이 없으면 에러 메시지. List가 아닌 Tree에서 파일을 선택했을 경우도 포함
            showErrorMessage("파일을 선택해주세요.", "Select File");
            return;
        }
        if(isFileInGitRepository()){ //.git 파일이 있는 경우 진행
            try{
                if(isStagedFile(currentFile)){ //선택한 파일이 stage 영역에 있는 경우 상태인 경우
                    int result = JOptionPane.showConfirmDialog(gui, "해당 파일 혹은 디렉토리를 stage 영역에서 제거하시겠습니까?", "git restore --staged", JOptionPane.ERROR_MESSAGE);
                    if(result == JOptionPane.OK_OPTION){ //restore --stage 실행 여부에서 확인을 받은 경우 git restore 명령어 수행
                        String[] gitRestoreStagedCommand = {"git", "restore","--staged", currentFile.getName()};
                        ProcessBuilder processBuilder = new ProcessBuilder(gitRestoreStagedCommand);
                        processBuilder.directory(currentFile.getParentFile());
                        Process process = processBuilder.start();

                        int commitStatus = process.waitFor(); //git restore --staged 명령어 정상 수행여부
                        if(commitStatus ==0){ // 정상수행을 의미
                            JOptionPane.showMessageDialog(gui, "해당 파일 혹은 디렉토리가 성공적으로 Stage 영역에서 제거되었습니다.");
                            System.out.println(currentFile);
                            System.out.println("Restored --staged");
                            try{
                                renderGitFileStatus(); // restore --staged  수행 완료한 경우, 파일에 변화가 일어났으므로 렌더링
                            }catch (IOException | GitAPIException e){
                                e.printStackTrace();
                            }
                        }else{ // 정상 수행이 아닌경우
                            showErrorMessage("Stage로 부터의 복원 과정이 정상적으로 이뤄지지 않았습니다.", "Git restore --staged error");
                        }
                    }else{return;} //사용자가 복원을 원치 않는 경우
                }
            }catch (IOException | InterruptedException e ){
                e.printStackTrace();
            }
        }else{ //.git이 존재하지 않는 경우
            showErrorMessage("선택한 파일은 .git repository에 존재하지 않습니다.", "Git restore --staged error");
        }

    }
    private void showErrorMessage(String errorMessage, String errorTitle) {
        JOptionPane.showMessageDialog(gui, errorMessage, errorTitle, JOptionPane.ERROR_MESSAGE);
    }

    private void showThrowable(Throwable t) {
        t.printStackTrace();
        JOptionPane.showMessageDialog(gui, t.toString(), t.getMessage(), JOptionPane.ERROR_MESSAGE);
        gui.repaint();
    }

    /**
     * Update the table on the EDT
     */
    private void setTableData(final File[] files) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                if (fileTableModel == null) {
                    fileTableModel = new FileTableModel();
                    table.setModel(fileTableModel);
                }
                table.getSelectionModel().removeListSelectionListener(listSelectionListener);
                fileTableModel.setFiles(files);
                table.getSelectionModel().addListSelectionListener(listSelectionListener);
                if (!cellSizesSet) {
                    Icon icon = fileSystemView.getSystemIcon(files[0]);

                    // size adjustment to better account for icons
                    table.setRowHeight(icon.getIconHeight() + rowIconPadding);

                    setColumnWidth(0, -1);
                    setColumnWidth(3, 60);
                    table.getColumnModel().getColumn(3).setMaxWidth(120);
                    setColumnWidth(4, -1);
                    setColumnWidth(5, -1);
                    setColumnWidth(6, -1);
                    setColumnWidth(7, -1);
                    setColumnWidth(8, -1);
                    setColumnWidth(9, -1);

                    cellSizesSet = true;
                }
            }
        });
    }

    private void setColumnWidth(int column, int width) {
        TableColumn tableColumn = table.getColumnModel().getColumn(column);
        if (width < 0) {
            // use the preferred width of the header..
            JLabel label = new JLabel((String) tableColumn.getHeaderValue());
            Dimension preferred = label.getPreferredSize();
            // altered 10->14 as per camickr comment.
            width = (int) preferred.getWidth() + 14;
        }
        tableColumn.setPreferredWidth(width);
        tableColumn.setMaxWidth(width);
        tableColumn.setMinWidth(width);
    }

    /**
     * Add the files that are contained within the directory of this node. Thanks to Hovercraft Full
     * Of Eels.
     */
    private void showChildren(final DefaultMutableTreeNode node) {
        tree.setEnabled(false);
        progressBar.setVisible(true);
        progressBar.setIndeterminate(true);

        SwingWorker<Void, File> worker = new SwingWorker<Void, File>() {
            @Override
            public Void doInBackground() {
                File file = (File) node.getUserObject();
                if (file.isDirectory()) {
                    File[] files = fileSystemView.getFiles(file, true); // !!
                    if (node.isLeaf()) {
                        for (File child : files) {
                            if (child.isDirectory()) {
                                publish(child);
                            }
                        }
                    }
                    setTableData(files);
                }
                return null;
            }

            @Override
            protected void process(List<File> chunks) {
                for (File child : chunks) {
                    node.add(new DefaultMutableTreeNode(child));
                }
            }

            @Override
            protected void done() {
                progressBar.setIndeterminate(false);
                progressBar.setVisible(false);
                tree.setEnabled(true);
            }
        };
        worker.execute();
    }

    /**
     * Update the File details view with the details of this File.
     */
    private void setFileDetails(File file) {
        currentFile = file;
        Icon icon = fileSystemView.getSystemIcon(file);
        fileName.setIcon(icon);
        fileName.setText(fileSystemView.getSystemDisplayName(file));
        path.setText(file.getPath());
        date.setText(new Date(file.lastModified()).toString());
        size.setText(file.length() + " bytes");
        readable.setSelected(file.canRead());
        writable.setSelected(file.canWrite());
        executable.setSelected(file.canExecute());
        isDirectory.setSelected(file.isDirectory());

        isFile.setSelected(file.isFile());

        JFrame f = (JFrame) gui.getTopLevelAncestor();
        if (f != null) {
            f.setTitle(APP_TITLE + " :: " + fileSystemView.getSystemDisplayName(file));
        }

        gui.repaint();
    }

    public static boolean copyFile(File from, File to) throws IOException {

        boolean created = to.createNewFile();

        if (created) {
            FileChannel fromChannel = null;
            FileChannel toChannel = null;
            try {
                fromChannel = new FileInputStream(from).getChannel();
                toChannel = new FileOutputStream(to).getChannel();

                toChannel.transferFrom(fromChannel, 0, fromChannel.size());

                // set the flags of the to the same as the from
                to.setReadable(from.canRead());
                to.setWritable(from.canWrite());
                to.setExecutable(from.canExecute());
            } finally {
                if (fromChannel != null) {
                    fromChannel.close();
                }
                if (toChannel != null) {
                    toChannel.close();
                }
                return false;
            }
        }
        return created;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                try {
                    // Significantly improves the look of the output in
                    // terms of the file names returned by FileSystemView!
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                } catch (Exception weTried) {
                }
                JFrame f = new JFrame(APP_TITLE);
                f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

                FileManager fileManager = new FileManager();
                f.setContentPane(fileManager.getGui());

                try {
                    URL urlBig = fileManager.getClass().getResource("fm-icon-32x32.png");
                    URL urlSmall = fileManager.getClass().getResource("fm-icon-16x16.png");
                    ArrayList<Image> images = new ArrayList<Image>();
                    images.add(ImageIO.read(urlBig));
                    images.add(ImageIO.read(urlSmall));
                    f.setIconImages(images);
                } catch (Exception weTried) {
                }

                f.pack();
                f.setLocationByPlatform(true);
                f.setMinimumSize(f.getSize());
                f.setVisible(true);

                fileManager.showRootFile();
            }
        });
    }
}

/**
 * A TableModel to hold File[].
 */
class FileTableModel extends AbstractTableModel {

    private File[] files;
    private FileSystemView fileSystemView = FileSystemView.getFileSystemView();
    private String[] columns = {"Icon", "File", "Path/name", "Size", "Last Modified", "R", "W", "E", "D", "F",};

    FileTableModel() {
        this(new File[0]);
    }

    FileTableModel(File[] files) {
        this.files = files;
    }

    public Object getValueAt(int row, int column) {
        File file = files[row];
        switch (column) {
            case 0:
                return fileSystemView.getSystemIcon(file);
            case 1:
                return fileSystemView.getSystemDisplayName(file);
            case 2:
                return file.getPath();
            case 3:
                return file.length();
            case 4:
                return file.lastModified();
            case 5:
                return file.canRead();
            case 6:
                return file.canWrite();
            case 7:
                return file.canExecute();
            case 8:
                return file.isDirectory();
            case 9:
                return file.isFile();
            default:
                System.err.println("Logic Error");
        }
        return "";
    }

    public int getColumnCount() {
        return columns.length;
    }

    public Class<?> getColumnClass(int column) {
        switch (column) {
            case 0:
                return ImageIcon.class;
            case 3:
                return Long.class;
            case 4:
                return Date.class;
            case 5:
            case 6:
            case 7:
            case 8:
            case 9:
                return Boolean.class;
        }
        return String.class;
    }

    public String getColumnName(int column) {
        return columns[column];
    }

    public int getRowCount() {
        return files.length;
    }

    public File getFile(int row) {
        return files[row];
    }

    public void setFiles(File[] files) {
        this.files = files;
        fireTableDataChanged();
    }
}

/**
 * A TreeCellRenderer for a File.
 */
class FileTreeCellRenderer extends DefaultTreeCellRenderer {

    private FileSystemView fileSystemView;

    private JLabel label;

    FileTreeCellRenderer() {
        label = new JLabel();
        label.setOpaque(true);
        fileSystemView = FileSystemView.getFileSystemView();
    }

    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {

        DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
        File file = (File) node.getUserObject();
        label.setIcon(fileSystemView.getSystemIcon(file));
        label.setText(fileSystemView.getSystemDisplayName(file));
        label.setToolTipText(file.getPath());

        if (selected) {
            label.setBackground(backgroundSelectionColor);
            label.setForeground(textSelectionColor);
        } else {
            label.setBackground(backgroundNonSelectionColor);
            label.setForeground(textNonSelectionColor);
        }

        return label;
    }
}
