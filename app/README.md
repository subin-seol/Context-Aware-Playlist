## File Structure:
app/
├── src/main/
│   ├── java/ (or kotlin/)           # Your code files
│   └── res/                         # Resources (layouts, colors, etc.)
│       ├── layout/                  # Screen layouts (.xml files)
│       ├── values/                  # Colors, strings, themes
│       ├── menu/                    # Menu definitions
│       ├── drawable/                # Icons, images
│       └── color/                   # Color selectors

## What each file does:

1. **activity_main.xml** (in res/layout/)
    - Defines how your main screen looks
    - Like HTML for your app screen
    - Contains views like buttons, text, navigation bars

2. **bottom_nav_menu.xml** (in res/menu/)
    - Defines what items appear in your bottom navigation
    - Just the items, not how they look

3. **colors.xml** (in res/values/)
    - Defines color variables you can reuse
    - Like CSS variables for colors

4. **themes.xml** (in res/values/)
    - Defines the overall look/style of your app
    - Dark theme, light theme, colors, etc.

5. **nav_item_color.xml** (in res/color/)
    - Special file that defines different colors for different states
    - Like: gray when not selected, blue when selected

---