/*******************************************************************************
 * Copyright (c) 2000, 2007 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation  - initial API and implementation
 *     Andrew Eisenberg - adaptation for AJDT
 *******************************************************************************/
package org.eclipse.ajdt.internal.ui.editor.quickfix;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import org.aspectj.ajde.core.AjCompiler;
import org.aspectj.ajde.core.IOutputLocationManager;
import org.eclipse.ajdt.core.AspectJPlugin;
import org.eclipse.ajdt.internal.core.ajde.CoreOutputLocationManager;
import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.ITextFileBuffer;
import org.eclipse.core.filebuffers.ITextFileBufferManager;
import org.eclipse.core.filebuffers.LocationKind;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.compiler.CharOperation;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.util.IClassFileReader;
import org.eclipse.jdt.core.util.IFieldInfo;
import org.eclipse.jdt.core.util.IInnerClassesAttribute;
import org.eclipse.jdt.core.util.IInnerClassesAttributeEntry;
import org.eclipse.jdt.core.util.IMethodInfo;
import org.eclipse.jdt.internal.corext.fix.AbstractSerialVersionOperationCore;
import org.eclipse.jdt.internal.corext.fix.LinkedProposalModel;
import org.eclipse.jdt.internal.corext.fix.LinkedProposalModelCore;
import org.eclipse.jdt.internal.corext.util.Messages;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.text.correction.CorrectionMessages;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;

/**
 * Proposal for a hashed serial version id.
 *
 * Adapted for AspectJ changes marked with // AspectJ Change
 *
 * @since 3.1
 */
public final class AJSerialVersionHashOperation extends AbstractSerialVersionOperationCore { // AspectJ Change

    private static final String STATIC_CLASS_INITIALIZER= "<clinit>"; //$NON-NLS-1$

    public static Long calculateSerialVersionId(ITypeBinding typeBinding, final IProgressMonitor monitor) throws CoreException, IOException {
        try {
            IFile classfileResource= getClassfile(typeBinding);
            if (classfileResource == null)
                return null;

          try (InputStream contents = classfileResource.getContents()) {
            IClassFileReader cfReader = ToolFactory.createDefaultClassFileReader(contents, IClassFileReader.ALL);
            if (cfReader != null) {
              return calculateSerialVersionId(cfReader);
            }
          }
            return null;
        } finally {
            if (monitor != null)
                monitor.done();
        }
    }

    private static String getClassName(char[] name) {
        return new String(name).replace('/', '.');
    }

    private static Long calculateSerialVersionId(IClassFileReader cfReader) throws IOException {
        // implementing algorithm specified on http://java.sun.com/j2se/1.5.0/docs/guide/serialization/spec/class.html#4100

        ByteArrayOutputStream os= new ByteArrayOutputStream();
        DataOutputStream doos= new DataOutputStream(os);
        doos.writeUTF(getClassName(cfReader.getClassName())); // class name
        int mod= getClassModifiers(cfReader);
//      System.out.println(Integer.toHexString(mod) + ' ' + Flags.toString(mod));

        int classModifiers= mod & (Flags.AccPublic | Flags.AccFinal | Flags.AccInterface | Flags.AccAbstract);

        doos.writeInt(classModifiers); // class modifiers
        char[][] interfaces= getSortedInterfacesNames(cfReader);
      for (char[] anInterface : interfaces) {
        doos.writeUTF(getClassName(anInterface));
      }
        IFieldInfo[] sortedFields= getSortedFields(cfReader);
      for (IFieldInfo curr : sortedFields) {
        int flags = curr.getAccessFlags();
        if (!Flags.isPrivate(flags) || (!Flags.isStatic(flags) && !Flags.isTransient(flags))) {
          doos.writeUTF(new String(curr.getName()));
          doos.writeInt(flags & (Flags.AccPublic | Flags.AccPrivate | Flags.AccProtected | Flags.AccStatic | Flags.AccFinal | Flags.AccVolatile | Flags.AccTransient)); // field modifiers
          doos.writeUTF(new String(curr.getDescriptor()));
        }
      }
        if (hasStaticClassInitializer(cfReader)) {
            doos.writeUTF(STATIC_CLASS_INITIALIZER);
            doos.writeInt(Flags.AccStatic);
            doos.writeUTF("()V"); //$NON-NLS-1$
        }
        IMethodInfo[] sortedMethods= getSortedMethods(cfReader);
      for (IMethodInfo curr : sortedMethods) {
        int flags = curr.getAccessFlags();
        if (!Flags.isPrivate(flags) && !curr.isClinit()) {
          doos.writeUTF(new String(curr.getName()));
          doos.writeInt(flags & (Flags.AccPublic | Flags.AccPrivate | Flags.AccProtected | Flags.AccStatic | Flags.AccFinal | Flags.AccSynchronized | Flags.AccNative | Flags.AccAbstract | Flags.AccStrictfp)); // method modifiers
          doos.writeUTF(getClassName(curr.getDescriptor()));
        }
      }
        doos.flush();
        return computeHash(os.toByteArray());
    }

    private static int getClassModifiers(IClassFileReader cfReader) {
        IInnerClassesAttribute innerClassesAttribute= cfReader.getInnerClassesAttribute();
        if (innerClassesAttribute != null) {
            IInnerClassesAttributeEntry[] entries = innerClassesAttribute.getInnerClassAttributesEntries();
          for (IInnerClassesAttributeEntry entry : entries) {
            char[] innerClassName = entry.getInnerClassName();
            if (innerClassName != null) {
              if (CharOperation.equals(cfReader.getClassName(), innerClassName)) {
                return entry.getAccessFlags();
              }
            }
          }
        }
        return cfReader.getAccessFlags();
    }



    private static Long computeHash(byte[] bytes) {
        try {
            byte[] sha= MessageDigest.getInstance("SHA-1").digest(bytes); //$NON-NLS-1$
            if (sha.length >= 8) {
                long hash= 0;
                for (int i= 7; i >= 0; i--) {
                    hash= (hash << 8) | (sha[i] & 0xFF);
                }
                return hash;
            }
        } catch (NoSuchAlgorithmException e) {
            JavaPlugin.log(e);
        }
        return null;
    }

    private static char[][] getSortedInterfacesNames(IClassFileReader cfReader) {
        char[][] interfaceNames= cfReader.getInterfaceNames();
        Arrays.sort(interfaceNames, CharOperation::compareTo);
        return interfaceNames;
    }

    private static IFieldInfo[] getSortedFields(IClassFileReader cfReader) {
        IFieldInfo[] allFields= cfReader.getFieldInfos();
        Arrays.sort(allFields, (o1, o2) -> CharOperation.compareTo(o1.getName(), o2.getName()));
        return allFields;
    }

    private static boolean hasStaticClassInitializer(IClassFileReader cfReader) {
      IMethodInfo[] methodInfos= cfReader.getMethodInfos();
      for (IMethodInfo methodInfo : methodInfos) {
        if (methodInfo.isClinit())
          return true;
      }
        return false;
    }

    private static IMethodInfo[] getSortedMethods(IClassFileReader cfReader) {
      IMethodInfo[] allMethods= cfReader.getMethodInfos();
      Arrays.sort(allMethods, (o1, o2) -> {
        if (o1.isConstructor() != o2.isConstructor())
          return o1.isConstructor() ? -1 : 1;
        else if (o1.isConstructor())
          return 0;
        int res = CharOperation.compareTo(o1.getName(), o2.getName());
        if (res != 0)
          return res;
        return CharOperation.compareTo(o1.getDescriptor(), o2.getDescriptor());
      });
      return allMethods;
    }

    // AspectJ Change begin
    /**
     * The original version of this method used the JDT state
     * object to find the class file of the type, but in
     * AspectJ projects, there is no State object.
     *
     * Instead use the IOutputLocationManager for the project
     *
     * Note that this will not work when the type is anonymous or it is
     * a named type inside a method declaration
     */
    private static IFile getClassfile(ITypeBinding typeBinding) throws CoreException {
        IType type = (IType) typeBinding.getJavaElement();

        // get the output location manager
        IProject project = typeBinding.getJavaElement().getJavaProject().getProject();
        AjCompiler compiler = AspectJPlugin.getDefault().getCompilerFactory().getCompilerForProject(project);
        IOutputLocationManager manager = compiler.getCompilerConfiguration().getOutputLocationManager();
        if (! (manager instanceof CoreOutputLocationManager))
          return null;
        CoreOutputLocationManager coreManager = (CoreOutputLocationManager) manager;

        // get the binary folder
        ICompilationUnit cu = type.getCompilationUnit();
        File file = new File(cu.getResource().getLocation().toOSString());
        File outLocation = coreManager.getOutputLocationForResource(file);

        // convert to IFolder
        IPath outPath = new Path(outLocation.getPath());
        IPath projPath = project.getLocation();
        if (projPath.isPrefixOf(outPath)) {
            outPath = outPath.removeFirstSegments(projPath.segmentCount());
        }
        IFolder outFolder = project.getFolder(outPath);
        if (!outFolder.exists()) {
            return null;
        }

        // get the package path
        IPackageFragment frag = type.getPackageFragment();
        String packageName = frag.getElementName();
        IPath packagePath = new Path(packageName.replace('.', '/'));
        IFolder packageFolder = outFolder.getFolder(packagePath);
        if (!packageFolder.exists()) {
            return null;
        }

        // determine the binary name of this type
        StringBuilder binaryName = new StringBuilder();
        IJavaElement enclosing = type.getParent();
        while (enclosing != null && ! (enclosing instanceof ICompilationUnit) ) {
            binaryName.append(enclosing.getElementName()).append("$");
            enclosing = enclosing.getParent();
        }
        binaryName.append(type.getElementName()).append(".class");
        IFile classFile = packageFolder.getFile(new Path(binaryName.toString()));
        if (classFile.exists()) {
            return classFile;
        } else {
            return null;
        }
    }
    // AspectJ Change end

    /**
     * Displays an appropriate error message for a specific problem.
     *
     * @param message
     *            The message to display
     */
    private static void displayErrorMessage(final String message) {
        final Display display= PlatformUI.getWorkbench().getDisplay();
        if (display != null && !display.isDisposed()) {
            display.asyncExec(() -> {
                if (!display.isDisposed()) {
                    final Shell shell= display.getActiveShell();
                    if (shell != null && !shell.isDisposed())
                        MessageDialog.openError(shell, CorrectionMessages.SerialVersionHashOperation_dialog_error_caption, Messages.format(CorrectionMessages.SerialVersionHashOperation_dialog_error_message, message));
                }
            });
        }
    }

    /**
     * Displays an appropriate error message for a specific problem.
     *
     * @param throwable
     *            the throwable object to display
     */
    private static void displayErrorMessage(final Throwable throwable) {
        displayErrorMessage(throwable.getLocalizedMessage());
    }

    /**
     * Displays a dialog with a question as message.
     *
     * @param title
     *            The title to display
     * @param message
     *            The message to display
     * @return returns the result of the dialog
     */
    private static boolean displayYesNoMessage(final String title, final String message) {
        final boolean[] result= { true};
        final Display display= PlatformUI.getWorkbench().getDisplay();
        if (display != null && !display.isDisposed()) {
            display.syncExec(() -> {
                if (!display.isDisposed()) {
                    final Shell shell= display.getActiveShell();
                    if (shell != null && !shell.isDisposed())
                        result[0]= MessageDialog.openQuestion(shell, title, message);
                }
            });
        }
        return result[0];
    }

    private final ICompilationUnit fCompilationUnit;

    public AJSerialVersionHashOperation(ICompilationUnit unit, ASTNode[] nodes) { // AspectJ Change
        super(unit, nodes);
        fCompilationUnit= unit;
    }

    /**
     * {@inheritDoc}
     * @throws CoreException
     */
 	 /* AJDT 1.7 */
     protected boolean addInitializer(final VariableDeclarationFragment fragment, final ASTNode declarationNode) {
        Assert.isNotNull(fragment);
        try {
            PlatformUI.getWorkbench().getProgressService().busyCursorWhile(monitor -> {
                Assert.isNotNull(monitor);
                String id= computeId(declarationNode, monitor);
                fragment.setInitializer(fragment.getAST().newNumberLiteral(id));
            });
        } catch (InvocationTargetException exception) {
            JavaPlugin.log(exception);
        } catch (InterruptedException exception) {
            // Do nothing
        }
        return true;
    }

    /**
     * {@inheritDoc}
     */
    protected void addLinkedPositions(ASTRewrite rewrite, VariableDeclarationFragment fragment, LinkedProposalModel positionGroups) {
        //Do nothing
    }

    private String computeId(final ASTNode declarationNode, final IProgressMonitor monitor) throws InterruptedException {
        Assert.isNotNull(monitor);
        long serialVersionID= SERIAL_VALUE;
        try {
            monitor.beginTask(CorrectionMessages.SerialVersionHashOperation_computing_id, 200);
            final IJavaProject project= fCompilationUnit.getJavaProject();
            final IPath path= fCompilationUnit.getResource().getFullPath();
            ITextFileBufferManager bufferManager= FileBuffers.getTextFileBufferManager();
            try {
                bufferManager.connect(path, LocationKind.IFILE, new SubProgressMonitor(monitor, 10));
                if (monitor.isCanceled())
                    throw new InterruptedException();

                final ITextFileBuffer buffer= bufferManager.getTextFileBuffer(path, LocationKind.IFILE);
                if (buffer.isDirty() && buffer.isStateValidated() && buffer.isCommitable() && displayYesNoMessage(CorrectionMessages.SerialVersionHashOperation_save_caption, CorrectionMessages.SerialVersionHashOperation_save_message))
                    buffer.commit(new SubProgressMonitor(monitor, 20), true);
                else
                    monitor.worked(20);

                if (monitor.isCanceled())
                    throw new InterruptedException();
            } finally {
                bufferManager.disconnect(path, LocationKind.IFILE, new SubProgressMonitor(monitor, 10));
            }
            project.getProject().build(IncrementalProjectBuilder.INCREMENTAL_BUILD, new SubProgressMonitor(monitor, 60));
            if (monitor.isCanceled())
                throw new InterruptedException();

            ITypeBinding typeBinding= getTypeBinding(declarationNode);
            if (typeBinding != null) {
                Long id= calculateSerialVersionId(typeBinding, new SubProgressMonitor(monitor, 100));
                if (id != null)
                    serialVersionID= id;
            }
        } catch (CoreException | IOException exception) {
            displayErrorMessage(exception);
        }
        finally {
            monitor.done();
        }
        return serialVersionID + LONG_SUFFIX;
    }

    private static ITypeBinding getTypeBinding(final ASTNode parent) {
        if (parent instanceof AbstractTypeDeclaration) {
            final AbstractTypeDeclaration declaration= (AbstractTypeDeclaration) parent;
            return declaration.resolveBinding();
        } else if (parent instanceof AnonymousClassDeclaration) {
            final AnonymousClassDeclaration declaration= (AnonymousClassDeclaration) parent;
            return declaration.resolveBinding();
        } else if (parent instanceof ParameterizedType) {
            final ParameterizedType type= (ParameterizedType) parent;
            return type.resolveBinding();
        }
        return null;
    }

    @Override
    protected void addLinkedPositions(ASTRewrite rewrite, VariableDeclarationFragment fragment,
      LinkedProposalModelCore positionGroups) {
      // TODO Auto-generated method stub
    }
}
