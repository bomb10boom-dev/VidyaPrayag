package com.littlebridge.enrollplus.ui.v2.screens.school

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.littlebridge.enrollplus.feature.admin.presentation.SchoolProfileState
import com.littlebridge.enrollplus.feature.admin.presentation.SchoolProfileViewModel
import com.littlebridge.enrollplus.ui.v2.components.VBackHeader
import com.littlebridge.enrollplus.ui.v2.components.VButton
import com.littlebridge.enrollplus.ui.v2.components.VButtonTone
import com.littlebridge.enrollplus.ui.v2.components.VButtonVariant
import com.littlebridge.enrollplus.ui.v2.components.VCard
import com.littlebridge.enrollplus.ui.v2.components.VInput
import com.littlebridge.enrollplus.ui.v2.screens.VSectionHeader
import com.littlebridge.enrollplus.ui.v2.screens.VStateHost
import com.littlebridge.enrollplus.ui.v2.screens.collectAsStateV2
import com.littlebridge.enrollplus.ui.v2.theme.VTheme
import com.littlebridge.enrollplus.ui.v2.theme.colored
import org.koin.compose.viewmodel.koinViewModel

/**
 * RA-47: EditSchoolProfileScreenV2 — the admin edits the live `schools` row
 * (the institutional record) via [SchoolProfileViewModel]
 * (`GET /api/v1/school/profile`, `PUT /api/v1/school/profile`). The server
 * resolves school_id from the JWT and enforces school-admin (never trusts the
 * body), so an admin can only ever edit their own school.
 *
 * Three states via [VStateHost] (LAW 3): loading while the row loads,
 * error+retry on a fatal load failure, and the editable form once loaded.
 * Save errors / confirmations are surfaced inline (dangerInk / successInk)
 * rather than failing silently. Portal overlay — back returns to Settings.
 */
@Composable
fun EditSchoolProfileScreenV2(
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: SchoolProfileViewModel = koinViewModel(),
)
{

    val state by viewModel.state.collectAsStateV2()


    Box(
        modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {


        Column(
            Modifier.fillMaxSize()
        ) {


            VBackHeader(
                title = "Institutional Profile",
                onBack = onBack
            )


            EditSchoolProfileContent(
                state = state,

                onName = viewModel::onName,
                onBoard = viewModel::onBoard,
                onMedium = viewModel::onMedium,
                onSchoolGender = viewModel::onSchoolGender,

                onContactPhone = viewModel::onContactPhone,
                onContactEmail = viewModel::onContactEmail,

                onPrincipalName = viewModel::onPrincipalName,
                onPrincipalPhone = viewModel::onPrincipalPhone,
                onPrincipalEmail = viewModel::onPrincipalEmail,

                onFullAddress = viewModel::onFullAddress,
                onCity = viewModel::onCity,
                onDistrict = viewModel::onDistrict,
                onState = viewModel::onState,
                onPincode = viewModel::onPincode,

                onSave = viewModel::save,
                onRetry = viewModel::load,

                modifier = Modifier
                    .weight(1f)
            )
        }



        // Floating Save Button
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),

            color = VTheme.colors.card,

            shadowElevation = 12.dp
        ) {

            Box(
                modifier = Modifier
                    .padding(
                        horizontal = 20.dp,
                        vertical = 12.dp
                    )
                    .navigationBarsPadding()
            ) {


                VButton(
                    text = "Save changes",

                    onClick = viewModel::save,

                    full = true,

                    variant =
                        VButtonVariant.Primary,

                    tone =
                        VButtonTone.Teal,

                    enabled =
                        !state.isSaving,

                    loading =
                        state.isSaving
                )
            }
        }
    }
}

@Composable
private fun EditSchoolProfileContent(
    state: SchoolProfileState,
    onName: (String) -> Unit,
    onBoard: (String) -> Unit,
    onMedium: (String) -> Unit,
    onSchoolGender: (String) -> Unit,
    onContactPhone: (String) -> Unit,
    onContactEmail: (String) -> Unit,
    onPrincipalName: (String) -> Unit,
    onPrincipalPhone: (String) -> Unit,
    onPrincipalEmail: (String) -> Unit,
    onFullAddress: (String) -> Unit,
    onCity: (String) -> Unit,
    onDistrict: (String) -> Unit,
    onState: (String) -> Unit,
    onPincode: (String) -> Unit,
    onSave: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {

    val c = VTheme.colors


    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(
                rememberScrollState()
            )
            .statusBarsPadding()
            .imePadding()
            .navigationBarsPadding()
            .padding(
                horizontal = 20.dp,
                vertical = 16.dp
            ),

        verticalArrangement =
            Arrangement.spacedBy(20.dp)
    ) {


        VStateHost(
            loading = state.isLoading,
            error = state.loadError,
            isEmpty = false,
            onRetry = onRetry,
        ) {


            // HEADER ------------------------------------------------

            Column(
                verticalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {

                Text(
                    text = "School profile",

                    style =
                        VTheme.type.h2
                            .colored(c.ink)
                )


                Text(
                    text =
                        "Keep your school's information accurate for parents, students and documents.",

                    style =
                        VTheme.type.body
                            .colored(c.ink3)
                )
            }



            // IDENTITY ------------------------------------------------

            EditSection(
                title = "School identity",
                subtitle = "Basic information",
                icon = Icons.Outlined.School
            ) {


                VInput(
                    value = state.name,
                    onValueChange = onName,
                    label = "School name",
                    placeholder =
                        "Little Bridge Public School"
                )


                VInput(
                    value = state.board,
                    onValueChange = onBoard,
                    label = "Board",
                    placeholder =
                        "CBSE / ICSE / State"
                )


                VInput(
                    value = state.medium,
                    onValueChange = onMedium,
                    label = "Medium",
                    placeholder =
                        "English"
                )


                VInput(
                    value = state.schoolGender,
                    onValueChange = onSchoolGender,
                    label = "School type",
                    placeholder =
                        "Co-ed / Boys / Girls"
                )
            }



            // CONTACT ------------------------------------------------

            EditSection(
                title = "Contact details",
                subtitle = "Public communication",
                icon = Icons.Outlined.Phone
            ) {


                VInput(
                    value = state.contactPhone,
                    onValueChange = onContactPhone,
                    label = "Phone",
                    placeholder =
                        "10-digit number",
                    keyboardType =
                        KeyboardType.Phone
                )


                VInput(
                    value = state.contactEmail,
                    onValueChange = onContactEmail,
                    label = "Email",
                    placeholder =
                        "office@school.edu",
                    keyboardType =
                        KeyboardType.Email
                )
            }




            // PRINCIPAL ------------------------------------------------

            EditSection(
                title = "Principal",
                subtitle = "Leadership contact",
                icon = Icons.Outlined.Person
            ) {


                VInput(
                    value = state.principalName,
                    onValueChange = onPrincipalName,
                    label = "Name",
                    placeholder =
                        "Full name"
                )


                VInput(
                    value = state.principalPhone,
                    onValueChange = onPrincipalPhone,
                    label = "Phone",
                    placeholder =
                        "10-digit number",
                    keyboardType =
                        KeyboardType.Phone
                )


                VInput(
                    value = state.principalEmail,
                    onValueChange = onPrincipalEmail,
                    label = "Email",
                    placeholder =
                        "principal@school.edu",
                    keyboardType =
                        KeyboardType.Email
                )
            }




            // ADDRESS ------------------------------------------------

            EditSection(
                title = "Location",
                subtitle = "School address",
                icon = Icons.Outlined.LocationOn
            ) {


                VInput(
                    value = state.fullAddress,
                    onValueChange = onFullAddress,
                    label = "Address",
                    placeholder =
                        "Street, area, landmark",
                    singleLine = false
                )


                Row(
                    horizontalArrangement =
                        Arrangement.spacedBy(12.dp)
                ) {

                    Box(
                        Modifier.weight(1f)
                    ) {
                        VInput(
                            value = state.city,
                            onValueChange = onCity,
                            label = "City"
                        )
                    }


                    Box(
                        Modifier.weight(1f)
                    ) {
                        VInput(
                            value = state.pincode,
                            onValueChange = onPincode,
                            label = "PIN",
                            keyboardType =
                                KeyboardType.Number
                        )
                    }
                }


                VInput(
                    value = state.district,
                    onValueChange = onDistrict,
                    label = "District"
                )


                VInput(
                    value = state.state,
                    onValueChange = onState,
                    label = "State"
                )
            }




            // FEEDBACK -----------------------------------------------

            state.errorMessage?.let {

                Text(
                    it,
                    style =
                        VTheme.type.body
                            .colored(c.dangerInk)
                )
            }


            state.infoMessage?.let {

                Text(
                    it,
                    style =
                        VTheme.type.body
                            .colored(c.successInk)
                )
            }



            Spacer(
                Modifier.height(70.dp)
            )
        }
    }



    // Floating save button
}

@Composable
private fun EditSection(
    title: String,
    subtitle: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {

    VCard {

        Column(
            verticalArrangement =
                Arrangement.spacedBy(14.dp)
        ) {


            Row(
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Icon(
                    icon,
                    contentDescription = null
                )


                Spacer(
                    Modifier.width(12.dp)
                )


                Column {

                    Text(
                        title,
                        style =
                            VTheme.type.body
                    )

                    Text(
                        subtitle,
                        style =
                            VTheme.type.caption
                    )
                }
            }


            HorizontalDivider()


            Column(
                verticalArrangement =
                    Arrangement.spacedBy(10.dp),
                content = content
            )
        }
    }
}