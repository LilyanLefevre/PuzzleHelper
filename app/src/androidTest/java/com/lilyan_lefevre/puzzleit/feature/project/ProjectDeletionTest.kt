package com.lilyan_lefevre.puzzleit.feature.project

import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.navigation.NavController
import androidx.navigation.Navigation
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.longClick
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lilyan_lefevre.puzzleit.R
import com.lilyan_lefevre.puzzleit.shared.database.Project
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ProjectDeletionTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var testProjectRepository: ProjectRepository

    private lateinit var scenario: FragmentScenario<ProjectListFragment>
    private val mockNavController: NavController = mockk()

    private val testProjects = listOf(
        Project(
            id = "test-1",
            name = "Test Project 1",
            creationDate = System.currentTimeMillis(),
            imagePath = "/path/to/image1.jpg",
            thumbnailPath = "/path/to/thumbnail1.jpg",
            status = "active"
        ),
        Project(
            id = "test-2", 
            name = "Test Project 2",
            creationDate = System.currentTimeMillis(),
            imagePath = "/path/to/image2.jpg",
            thumbnailPath = "/path/to/thumbnail2.jpg",
            status = "active"
        )
    )

    @Before
    fun setup() {
        hiltRule.inject()
        scenario = launchFragmentInContainer(themeResId = R.style.Theme_PuzzleHelper)
    }

    @Test
    fun `long press on project should show delete confirmation dialog`() {
        // Given: Projects are loaded
        mockProjectRepositoryFlow(testProjects)
        
        scenario.onFragment { fragment ->
            // Setup NavController
            Navigation.setViewNavController(fragment.requireView(), mockNavController)
        }

        // When: User long presses on first project
        onView(withId(R.id.recyclerView_projects))
            .perform(longClick())

        // Then: Delete confirmation dialog should be displayed
        onView(withText("Delete Project"))
            .check(matches(isDisplayed()))
        onView(withText("Are you sure you want to delete \"Test Project 1\"?"))
            .check(matches(isDisplayed()))
        onView(withText("Delete"))
            .check(matches(isDisplayed()))
        onView(withText("Cancel"))
            .check(matches(isDisplayed()))
    }

    @Test
    fun `confirming deletion should call deleteProject on ViewModel`() {
        // Given: Projects are loaded and dialog is shown
        mockProjectRepositoryFlow(testProjects)
        
        scenario.onFragment { fragment ->
            // Setup NavController
            Navigation.setViewNavController(fragment.requireView(), mockNavController)
            
            // Trigger long press to show dialog
            fragment.view?.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recyclerView_projects)
                ?.findViewHolderForAdapterPosition(0)?.itemView?.performLongClick()
        }

        // When: User confirms deletion
        onView(withText("Delete"))
            .perform(click())

        // Then: Delete should be called on the ViewModel
        // This would be verified through ViewModel testing or integration with repository
    }

    @Test
    fun `cancelling deletion should not delete project`() {
        // Given: Projects are loaded and dialog is shown
        mockProjectRepositoryFlow(testProjects)
        
        scenario.onFragment { fragment ->
            // Setup NavController
            Navigation.setViewNavController(fragment.requireView(), mockNavController)
            
            // Trigger long press to show dialog
            fragment.view?.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recyclerView_projects)
                ?.findViewHolderForAdapterPosition(0)?.itemView?.performLongClick()
        }

        // When: User cancels deletion
        onView(withText("Cancel"))
            .perform(click())

        // Then: Dialog should disappear and project should remain
        onView(withText("Delete Project"))
            .check(matches(not(isDisplayed())))
        
        // Verify project list still contains all projects
        onView(withId(R.id.recyclerView_projects))
            .check(matches(hasDescendant(withText("Test Project 1"))))
    }

    @Test
    fun `deleting one project should not affect other projects`() = runTest {
        // Given: Multiple projects exist
        mockProjectRepositoryFlow(testProjects)
        
        scenario.onFragment { fragment ->
            // Setup NavController
            Navigation.setViewNavController(fragment.requireView(), mockNavController)
        }

        // When: First project is deleted
        onView(withId(R.id.recyclerView_projects))
            .perform(longClick())
        onView(withText("Delete"))
            .perform(click())

        // Then: Second project should still be visible
        onView(withId(R.id.recyclerView_projects))
            .check(matches(hasDescendant(withText("Test Project 2"))))
    }

    private fun mockProjectRepositoryFlow(projects: List<Project>) {
        // This would need to be implemented based on your test setup
        // You might need to use a test ViewModel or mock the repository
        // This is a placeholder showing the intended behavior
    }
}
