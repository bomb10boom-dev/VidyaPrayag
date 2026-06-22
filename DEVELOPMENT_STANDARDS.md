# Vidya Prayag - Development Standards: Adding a New Screen

This guide outlines the standard architecture and implementation patterns to follow when adding a new screen to the Vidya Prayag project.

---

## 1. Domain Layer (Data & Logic)

Always start with the **Domain Layer** in the `shared` module. This layer is platform-independent.

### 1.1 Data Models
Define data models as `@Serializable` data classes in `shared/src/commonMain/kotlin/com.littlebridge.enrollplus/domain/model/`.

```kotlin
@Serializable
data class MyFeatureItem(
    val id: String,
    val title: String,
    // ...
)
```

### 1.2 Repository Interface
Define the repository interface in `shared/src/commonMain/kotlin/com.littlebridge.enrollplus/domain/repository/`.

```kotlin
interface MyFeatureRepository {
    fun getItems(): Flow<List<MyFeatureItem>>
    suspend fun refresh()
}
```

### 1.3 Use Cases
Create focused Use Case classes in `shared/src/commonMain/kotlin/com.littlebridge.enrollplus/domain/usecase/`. Use cases should represent a single business operation.

```kotlin
class GetMyFeatureItemsUseCase(private val repository: MyFeatureRepository) {
    operator fun invoke(): Flow<List<MyFeatureItem>> = repository.getItems()
    suspend fun refresh() = repository.refresh()
}
```

---

## 2. Data Layer (Implementation)

Implement the repository and data sources in the `shared` module.

### 2.1 Remote Data Source (Ktor)
Add API calls to a dedicated class in `shared/src/commonMain/kotlin/com.littlebridge.enrollplus/data/remote/`. Use the injected `HttpClient`.

### 2.2 Repository Implementation
Implement the repository in `shared/src/commonMain/kotlin/com.littlebridge.enrollplus/data/repository/`. Coordinate between local (Room) and remote (Ktor) data sources.

---

## 3. Presentation Layer (ViewModel)

Create the ViewModel in `shared/src/commonMain/kotlin/com.littlebridge.enrollplus/presentation/`.

- Extend `androidx.lifecycle.ViewModel`.
- Use `StateFlow` for UI state.
- Inject Use Cases via constructor.

```kotlin
class MyFeatureViewModel(
    private val getItemsUseCase: GetMyFeatureItemsUseCase
) : ViewModel() {
    
    val items: StateFlow<List<MyFeatureItem>> = getItemsUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
```

---

## 4. UI Layer (Jetpack Compose)

Implement the UI in the `composeApp` module.

### 4.1 Composable Screens
Place screen-level composables in `composeApp/src/commonMain/kotlin/com.littlebridge.enrollplus/ui/screens/`.

- Use **Stateless Composables**: Pass the state and callbacks into the composable.
- Use **ViewModel Injection**: Use Koin's `koinViewModel()` or `getViewModel()` to get the ViewModel.

### 4.2 Theme Usage
Always use `MaterialTheme.colorScheme` and `MaterialTheme.typography`. Do **not** hardcode colors.

```kotlin
Text(
    text = "Hello",
    color = MaterialTheme.colorScheme.primary,
    style = MaterialTheme.typography.titleMedium
)
```

### 4.3 App Theme Component
Wrap your screen with `VidyaPrayagTheme` to ensure proper support for Light, Dark, and Midnight modes.

---

## 5. Dependency Injection (Koin)

Register all new classes in `shared/src/commonMain/kotlin/com.littlebridge.enrollplus/di/Koin.kt`.

- **Repositories**: `single<MyFeatureRepository> { MyFeatureRepositoryImpl(get(), get()) }`
- **Use Cases**: `factory { GetMyFeatureItemsUseCase(get()) }`
- **ViewModels**: Add to `viewModelModule` using `factory { MyFeatureViewModel(get()) }`

---

## Summary Checklist
1. [ ] Define **Data Model** (`shared/domain/model`)
2. [ ] Define **Repository Interface** (`shared/domain/repository`)
3. [ ] Implement **Repository** & **Data Sources** (`shared/data`)
4. [ ] Create **Use Case** (`shared/domain/usecase`)
5. [ ] Create **ViewModel** (`shared/presentation`)
6. [ ] Register in **Koin** (`shared/di/Koin.kt`)
7. [ ] Create **UI Screen** (`composeApp/ui/screens`)
8. [ ] Integrate into **Navigation** (`composeApp/App.kt`)
