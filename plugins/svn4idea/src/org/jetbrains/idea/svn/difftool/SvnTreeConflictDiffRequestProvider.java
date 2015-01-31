package org.jetbrains.idea.svn.difftool;

import com.intellij.openapi.progress.BackgroundTaskQueue;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.diff.api.DiffContext;
import com.intellij.openapi.util.diff.api.FrameDiffTool;
import com.intellij.openapi.util.diff.chains.DiffRequestPresentableException;
import com.intellij.openapi.util.diff.impl.ModifiablePanel;
import com.intellij.openapi.util.diff.requests.DiffRequest;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestPresentable;
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProvider;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.ConflictedSvnChange;
import org.jetbrains.idea.svn.conflict.TreeConflictDescription;
import org.jetbrains.idea.svn.treeConflict.TreeConflictRefreshablePanel;

import javax.swing.*;

public class SvnTreeConflictDiffRequestProvider implements ChangeDiffRequestProvider {
  @NotNull
  @Override
  public ThreeState isEquals(@NotNull Change change1, @NotNull Change change2) {
    if (change1 instanceof ConflictedSvnChange && change2 instanceof ConflictedSvnChange) {
      if (!change1.isTreeConflict() && !change2.isTreeConflict()) return ThreeState.UNSURE;
      if (!change1.isTreeConflict() || !change2.isTreeConflict()) return ThreeState.NO;

      TreeConflictDescription description1 = ((ConflictedSvnChange)change1).getBeforeDescription();
      TreeConflictDescription description2 = ((ConflictedSvnChange)change2).getBeforeDescription();
      return TreeConflictRefreshablePanel.descriptionsEqual(description1, description2) ? ThreeState.YES : ThreeState.NO;
    }
    return ThreeState.UNSURE;
  }

  @Override
  public boolean canCreate(@NotNull Project project, @NotNull Change change) {
    return change instanceof ConflictedSvnChange && ((ConflictedSvnChange)change).getConflictState().isTree();
  }

  @NotNull
  @Override
  public DiffRequest process(@NotNull ChangeDiffRequestPresentable presentable,
                             @NotNull UserDataHolder context,
                             @NotNull ProgressIndicator indicator) throws DiffRequestPresentableException, ProcessCanceledException {
    return new SvnTreeConflictDiffRequest(((ConflictedSvnChange)presentable.getChange()));
  }

  public static class SvnTreeConflictDiffRequest extends DiffRequest {
    @NotNull private final ConflictedSvnChange myChange;

    public SvnTreeConflictDiffRequest(@NotNull ConflictedSvnChange change) {
      myChange = change;
    }

    @NotNull
    public ConflictedSvnChange getChange() {
      return myChange;
    }

    @Nullable
    @Override
    public String getTitle() {
      return ChangeDiffRequestPresentable.getRequestTitle(myChange);
    }
  }

  public static class SvnTreeConflictDiffTool implements FrameDiffTool {
    @NotNull
    @Override
    public String getName() {
      return "SVN Phantom Changes Viewer";
    }

    @Override
    public boolean canShow(@NotNull DiffContext context, @NotNull DiffRequest request) {
      return request instanceof SvnTreeConflictDiffRequest;
    }

    @NotNull
    @Override
    public DiffViewer createComponent(@NotNull DiffContext context, @NotNull DiffRequest request) {
      return new SvnTreeConflictDiffViewer(context, (SvnTreeConflictDiffRequest)request);
    }
  }

  private static class SvnTreeConflictDiffViewer implements FrameDiffTool.DiffViewer {
    @NotNull private final DiffContext myContext;
    @NotNull private final SvnTreeConflictDiffRequest myRequest;
    @NotNull private final ModifiablePanel myPanel = new ModifiablePanel();

    @NotNull private final BackgroundTaskQueue myQueue;
    @NotNull private final TreeConflictRefreshablePanel myDelegate;

    public SvnTreeConflictDiffViewer(@NotNull DiffContext context, @NotNull SvnTreeConflictDiffRequest request) {
      myContext = context;
      myRequest = request;

      myQueue = new BackgroundTaskQueue(myContext.getProject(), "Loading change details");

      // We don't need to listen on File/Document, because panel always will be the same for a single change (@see myDelegate.isStillValid())
      // And if Change will change - we'll create new DiffRequest and DiffViewer
      myDelegate =
        new TreeConflictRefreshablePanel(myContext.getProject(), "Loading tree conflict details", myQueue, myRequest.getChange());
      myDelegate.refresh();
      myPanel.setContent(myDelegate.getPanel());
    }

    @NotNull
    @Override
    public JComponent getComponent() {
      return myPanel;
    }

    @Nullable
    @Override
    public JComponent getPreferredFocusedComponent() {
      return myPanel;
    }

    @NotNull
    @Override
    public FrameDiffTool.ToolbarComponents init() {
      return new FrameDiffTool.ToolbarComponents();
    }

    @Override
    public void dispose() {
      myQueue.clear();
    }
  }
}
